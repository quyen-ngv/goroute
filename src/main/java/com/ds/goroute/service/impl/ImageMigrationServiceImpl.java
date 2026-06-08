package com.ds.goroute.service.impl;

import com.ds.goroute.service.ImageMigrationService;
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
public class ImageMigrationServiceImpl implements ImageMigrationService {

    private final StorageService storageService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${imgpress.service.url:http://imgpress:3000}")
    private String imgpressUrl;
    
    @Value("${image.migration.max-threads:10}")
    private int maxThreads;
    
    @Value("${image.migration.timeout-seconds:30}")
    private int timeoutSeconds;

    private static final int MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024; // 10MB

    @Override
    public String migrateImage(String imageUrl, String targetPath) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return imageUrl;
        }
        
        try {
            log.debug("Migrating image: {}", imageUrl);
            
            // 1. Download image from Google (try even private URLs)
            byte[] imageBytes = downloadImage(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("Failed to download image: {}", imageUrl);
                return imageUrl;
            }
            
            // 2. Compress via imgpress
            byte[] compressedBytes;
            try {
                compressedBytes = compressImage(imageBytes, extractFilename(imageUrl));
                log.debug("Image compressed: {} -> {} bytes", imageBytes.length, compressedBytes.length);
            } catch (Exception e) {
                log.warn("Compression failed, using original if size acceptable: {}", e.getMessage());
                if (imageBytes.length > MAX_IMAGE_SIZE_BYTES) {
                    log.error("Image too large to upload without compression: {} bytes", imageBytes.length);
                    return imageUrl;
                }
                compressedBytes = imageBytes;
            }
            
            // 3. Upload to MinIO
            String filename = targetPath + UUID.randomUUID() + ".webp";
            String newUrl = storageService.uploadBytes(compressedBytes, filename, "image/webp");
            
            log.info("Image migrated successfully: {} -> {}", imageUrl, newUrl);
            return newUrl;
            
        } catch (Exception e) {
            log.error("Failed to migrate image {}: {}", imageUrl, e.getMessage());
            return imageUrl;
        }
    }
    
    /**
     * Check if URL is a private/authenticated Google URL that cannot be downloaded
     */
    private boolean isPrivateGoogleUrl(String url) {
        if (url == null) {
            return false;
        }
        // gps-cs-s and gps-cs URLs are private and require authentication
        return url.contains("/gps-cs-s/") || 
               url.contains("/gps-cs/") ||
               url.contains("googleusercontent.com/gps-cs");
    }

    @Override
    public Map<String, String> migrateImages(List<String> imageUrls, String targetPath) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Map<String, String> resultMap = new ConcurrentHashMap<>();
        ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
        
        try {
            List<CompletableFuture<Void>> futures = imageUrls.stream()
                    .map(imageUrl -> CompletableFuture.runAsync(() -> {
                        String newUrl = migrateImage(imageUrl, targetPath);
                        if (newUrl != null) {
                            resultMap.put(imageUrl, newUrl);
                        }
                    }, executor))
                    .toList();
            
            // Wait for all tasks with timeout
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(timeoutSeconds, TimeUnit.SECONDS);
            
            log.info("Migrated {}/{} images successfully", resultMap.size(), imageUrls.size());
            
        } catch (TimeoutException e) {
            log.warn("Image migration timeout after {} seconds", timeoutSeconds);
        } catch (Exception e) {
            log.error("Error in batch image migration: {}", e.getMessage());
        } finally {
            executor.shutdownNow();
        }
        
        return resultMap;
    }

    @Override
    public String migrateImagesJson(String imagesJson, String targetPath) {
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
            
            // Extract all image URLs
            for (JsonNode node : arrayNode) {
                if (node.has("image") && node.get("image").isTextual()) {
                    imageUrls.add(node.get("image").asText());
                }
            }
            
            if (imageUrls.isEmpty()) {
                return imagesJson;
            }
            
            // Migrate all images in parallel
            Map<String, String> migratedUrls = migrateImages(imageUrls, targetPath);
            
            // Update JSON with new URLs (keep original if migration failed)
            ArrayNode newArrayNode = objectMapper.createArrayNode();
            for (JsonNode node : arrayNode) {
                if (node.has("image") && node.get("image").isTextual()) {
                    String oldUrl = node.get("image").asText();
                    String newUrl = migratedUrls.getOrDefault(oldUrl, oldUrl);
                    
                    ObjectNode newNode = ((ObjectNode) node).deepCopy();
                    newNode.put("image", newUrl);
                    newArrayNode.add(newNode);
                }
            }
            
            return objectMapper.writeValueAsString(newArrayNode);
            
        } catch (Exception e) {
            log.error("Error migrating images from JSON: {}", e.getMessage());
            return imagesJson;
        }
    }
    
    private byte[] downloadImage(String imageUrl) {
        try {
            // Try multiple strategies for Google URLs
            List<String> urlsToTry = new ArrayList<>();
            
            // Strategy 1: Try original URL first
            urlsToTry.add(imageUrl);
            
            // Strategy 2: If it's a Google URL, try different size params
            if (imageUrl.contains("googleusercontent.com")) {
                // Remove size params and try =s0 (original size)
                String baseUrl = imageUrl.split("=")[0];
                urlsToTry.add(baseUrl + "=s0");
                urlsToTry.add(baseUrl + "=s4096");
                urlsToTry.add(baseUrl + "=w4096");
                urlsToTry.add(baseUrl); // Try base without params
            }
            
            // Try each URL with enhanced headers
            for (String url : urlsToTry) {
                try {
                    byte[] result = tryDownload(url);
                    if (result != null && result.length > 0) {
                        log.debug("Successfully downloaded from: {}", url);
                        return result;
                    }
                } catch (Exception e) {
                    log.debug("Failed attempt with URL {}: {}", url, e.getMessage());
                }
            }
            
            log.error("All download attempts failed for: {}", imageUrl);
            return null;
            
        } catch (Exception e) {
            log.error("Failed to download image {}: {}", imageUrl, e.getMessage());
            return null;
        }
    }
    
    private byte[] tryDownload(String url) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        headers.set("Referer", "https://www.google.com/maps/");
        headers.set("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
        headers.set("Accept-Language", "en-US,en;q=0.9");
        headers.set("Accept-Encoding", "gzip, deflate, br");
        headers.set("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
        headers.set("sec-ch-ua-mobile", "?0");
        headers.set("sec-ch-ua-platform", "\"Windows\"");
        headers.set("sec-fetch-dest", "image");
        headers.set("sec-fetch-mode", "no-cors");
        headers.set("sec-fetch-site", "cross-site");
        headers.set("Cache-Control", "no-cache");
        headers.set("Pragma", "no-cache");
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                byte[].class
        );
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            return response.getBody();
        }
        
        throw new Exception("HTTP " + response.getStatusCode());
    }
    
    private byte[] compressImage(byte[] imageBytes, String filename) throws Exception {
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
        
        String url = imgpressUrl + "/compress/one?format=webp&quality=80&width=1600&autoOrient=true";
        ResponseEntity<Map> response = restTemplate.postForEntity(url, requestEntity, Map.class);
        
        if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
            Map<String, Object> responseBody = response.getBody();
            Boolean error = (Boolean) responseBody.get("error");
            
            if (error != null && error) {
                throw new Exception("Compression service returned error: " + responseBody.get("message"));
            }
            
            String base64Data = (String) responseBody.get("data");
            if (base64Data != null) {
                return Base64.getDecoder().decode(base64Data);
            }
        }
        
        throw new Exception("Invalid response from compression service");
    }
    
    private String extractFilename(String url) {
        try {
            String[] parts = url.split("/");
            String lastPart = parts[parts.length - 1];
            
            // Remove query params
            int queryIndex = lastPart.indexOf("?");
            if (queryIndex > 0) {
                lastPart = lastPart.substring(0, queryIndex);
            }
            
            // Remove size params
            int paramIndex = lastPart.indexOf("=");
            if (paramIndex > 0) {
                lastPart = lastPart.substring(0, paramIndex);
            }
            
            return lastPart.isEmpty() ? "image.jpg" : lastPart;
        } catch (Exception e) {
            return "image.jpg";
        }
    }
}
