package com.ds.goroute.service.impl;

import com.ds.goroute.service.ImageArchiveService;
import com.ds.goroute.service.StorageService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageArchiveServiceImpl implements ImageArchiveService {

    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${imgpress.service.url:http://imgpress:3000}")
    private String imgpressUrl;
    
    @Value("${image.archive.max-threads:8}")
    private int maxThreads;
    
    @Value("${image.archive.timeout-seconds:60}")
    private int timeoutSeconds;

    private static final int COMPRESSION_QUALITY = 40;
    private static final int MAX_WIDTH = 1200;

    @Override
    public String compressAndDelete(String imageUrl, String targetPath) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.warn("Cannot compress: imageUrl is null or empty");
            return imageUrl;
        }
        
        if (!isMinIOUrl(imageUrl)) {
            log.debug("Skipping non-MinIO URL: {}", imageUrl);
            return imageUrl;
        }
        
        try {
            log.info("🗜️ Compress & delete: {}", imageUrl);
            
            byte[] originalBytes = downloadFromMinIO(imageUrl);
            if (originalBytes == null || originalBytes.length == 0) {
                log.error("❌ Failed to download for compression: {}", imageUrl);
                return imageUrl;
            }
            
            long originalSize = originalBytes.length;
            log.info("📥 Downloaded: {}KB", originalSize / 1024);
            
            byte[] compressedBytes = compressImageAggressively(originalBytes, extractFilename(imageUrl));
            if (compressedBytes == null) {
                log.error("❌ Compression failed: {}", imageUrl);
                return imageUrl;
            }
            
            long compressedSize = compressedBytes.length;
            double compressionRatio = (double) compressedSize / originalSize * 100;
            
            String compressedFilename = targetPath + generateCompressedFilename();
            String newUrl = storageService.uploadBytes(compressedBytes, compressedFilename, "image/webp");
            
            if (newUrl != null) {
                // Try to delete original, but continue even if delete fails
                boolean deleted = deleteOriginal(imageUrl);
                if (deleted) {
                    log.info("✅ Compressed & deleted: {} -> {} ({}KB -> {}KB, {:.1f}%)", 
                            imageUrl, newUrl, originalSize/1024, compressedSize/1024, compressionRatio);
                } else {
                    log.warn("⚠️ Compressed successfully but failed to delete original: {}", imageUrl);
                    log.warn("⚠️ Using compressed version, original still exists: {}", newUrl);
                }
                // Return new URL even if delete failed - compressed version is available
                return newUrl;
            } else {
                log.error("❌ Failed to upload compressed image");
                return imageUrl;
            }
            
        } catch (Exception e) {
            log.error("❌ Failed compress & delete {}: {}", imageUrl, e.getMessage(), e);
            return imageUrl;
        }
    }

    @Override
    public String compressImagesJsonDirectly(String imagesJson, String targetPath) {
        if (imagesJson == null || imagesJson.trim().isEmpty() || imagesJson.equals("[]")) {
            return "[]";
        }
        
        try {
            JsonNode rootNode = objectMapper.readTree(imagesJson);
            
            if (!rootNode.isArray()) {
                log.warn("Images JSON is not an array");
                return imagesJson;
            }
            
            ArrayNode arrayNode = (ArrayNode) rootNode;
            List<String> imageUrls = new ArrayList<>();
            
            for (JsonNode node : arrayNode) {
                if (node.has("image") && node.get("image").isTextual()) {
                    String url = node.get("image").asText();
                    if (isMinIOUrl(url)) {
                        imageUrls.add(url);
                    }
                }
            }
            
            if (imageUrls.isEmpty()) {
                return imagesJson;
            }
            
            Map<String, String> compressedUrls = archiveAndCompressBatch(imageUrls, targetPath);
            
            // Try to delete originals, but continue even if some fail
            for (String originalUrl : compressedUrls.keySet()) {
                boolean deleted = deleteOriginal(originalUrl);
                if (!deleted) {
                    log.warn("⚠️ Failed to delete after compression (continuing anyway): {}", originalUrl);
                }
            }
            
            ArrayNode newArrayNode = objectMapper.createArrayNode();
            for (JsonNode node : arrayNode) {
                if (node.has("image") && node.get("image").isTextual()) {
                    String oldUrl = node.get("image").asText();
                    String newUrl = compressedUrls.getOrDefault(oldUrl, oldUrl);
                    
                    ObjectNode newNode = ((ObjectNode) node).deepCopy();
                    newNode.put("image", newUrl);
                    newArrayNode.add(newNode);
                } else {
                    newArrayNode.add(node);
                }
            }
            
            return objectMapper.writeValueAsString(newArrayNode);
            
        } catch (Exception e) {
            log.error("Error compressing images from JSON: {}", e.getMessage());
            return imagesJson;
        }
    }

    @Override
    public String archiveAndCompress(String imageUrl, String targetPath) {
        return compressAndDelete(imageUrl, targetPath);
    }

    @Override
    public Map<String, String> archiveAndCompressBatch(List<String> imageUrls, String targetPath) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyMap();
        }
        
        List<String> minioUrls = imageUrls.stream()
                .filter(this::isMinIOUrl)
                .toList();
                
        if (minioUrls.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, String> resultMap = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        
        try {
            List<CompletableFuture<Void>> futures = minioUrls.stream()
                    .map(imageUrl -> CompletableFuture.runAsync(() -> {
                        String newUrl = compressAndDelete(imageUrl, targetPath);
                        if (newUrl != null && !newUrl.equals(imageUrl)) {
                            resultMap.put(imageUrl, newUrl);
                        }
                    }, executor))
                    .toList();
            
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            
            log.info("Compressed {}/{} images successfully", resultMap.size(), minioUrls.size());
            
        } catch (Exception e) {
            log.error("Error in batch compression: {}", e.getMessage());
        } finally {
            executor.shutdownNow();
        }
        
        return resultMap;
    }

    @Override
    public String moveToArchive(String imageUrl, String archivePath) {
        log.debug("moveToArchive not used in direct compression mode");
        return null;
    }

    @Override
    public boolean deleteOriginal(String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            log.warn("Cannot delete original: imageUrl is null or empty");
            return false;
        }
        
        if (!isMinIOUrl(imageUrl)) {
            log.warn("Cannot delete original: not a MinIO URL: {}", imageUrl);
            return false;
        }
        
        try {
            log.info("🗑️ Deleting original: {}", imageUrl);
            storageService.deleteFile(imageUrl);
            log.info("✅ Successfully deleted: {}", imageUrl);
            return true;
            
        } catch (Exception e) {
            log.error("❌ Failed to delete {}: {}", imageUrl, e.getMessage(), e);
            return false;
        }
    }

    @Override
    public String archiveAndCompressImagesJson(String imagesJson, String targetPath, String archivePath) {
        return compressImagesJsonDirectly(imagesJson, targetPath);
    }

    // Helper methods

    private boolean isMinIOUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        // Only process old images, not already in resources folder
        return url.contains("onestudy.id.vn") 
                && !url.startsWith("http://") 
                && !url.contains("google")
                && !url.contains("/resources/"); // Skip already processed
    }

    private byte[] downloadFromMinIO(String imageUrl) {
        try {
            HttpHeaders headers = new HttpHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);
            
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    imageUrl,
                    HttpMethod.GET,
                    entity,
                    byte[].class
            );
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                return response.getBody();
            }
            
        } catch (Exception e) {
            log.error("Failed to download from MinIO {}: {}", imageUrl, e.getMessage());
        }
        
        return null;
    }

    private byte[] compressImageAggressively(byte[] imageBytes, String filename) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            });
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            String url = String.format("%s/compress/one?format=webp&quality=%d&width=%d&autoOrient=true",
                    imgpressUrl, COMPRESSION_QUALITY, MAX_WIDTH);
                    
            ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Boolean error = (Boolean) responseBody.get("error");
                
                if (error == null || !error) {
                    String base64Data = (String) responseBody.get("data");
                    if (base64Data != null) {
                        return Base64.getDecoder().decode(base64Data);
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("Aggressive compression failed: {}", e.getMessage());
        }
        
        return null;
    }

    private String generateCompressedFilename() {
        return "compressed_" + UUID.randomUUID() + ".webp";
    }

    private String extractFilename(String url) {
        try {
            String[] parts = url.split("/");
            String lastPart = parts[parts.length - 1];
            
            int queryIndex = lastPart.indexOf("?");
            if (queryIndex > 0) {
                lastPart = lastPart.substring(0, queryIndex);
            }
            
            return lastPart.isEmpty() ? "image.jpg" : lastPart;
        } catch (Exception e) {
            return "image.jpg";
        }
    }
}
