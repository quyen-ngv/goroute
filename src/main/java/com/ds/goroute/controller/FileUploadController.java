package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/v1/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController extends BaseService {

    private final StorageService storageService;
    private final RestTemplate restTemplate;

    @Value("${imgpress.service.url:http://imgpress:3000}")
    private String imgpressUrl;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024; // 2MB

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") UUID userId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ofFailed(400, "File is empty"));
            }

            // Validate file type
            String contentType = file.getContentType();
            if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                return ResponseEntity.badRequest()
                        .body(ofFailed(400, "Invalid file type. Only JPG, PNG, and WEBP are allowed"));
            }

            byte[] imageBytes = file.getBytes();
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : ".jpg";

            // Try to compress image
            try {
                imageBytes = compressImage(imageBytes, originalFilename);
                log.info("Image compressed successfully for user: {}", userId);
            } catch (Exception e) {
                log.warn("Image compression failed, checking file size: {}", e.getMessage());
                if (imageBytes.length > MAX_FILE_SIZE_BYTES) {
                    return ResponseEntity.badRequest()
                            .body(ofFailed(400, "File size exceeds 2MB limit"));
                }
                log.info("File size acceptable, proceeding without compression");
            }

            // Generate unique filename
            String fileName = "expenses/" + userId + "/" + UUID.randomUUID() + extension;

            // Upload to S3
            String fileUrl = storageService.uploadFile(
                    fileName,
                    new ByteArrayInputStream(imageBytes),
                    contentType,
                    (long) imageBytes.length
            );

            log.info("File uploaded successfully: {}", fileUrl);
            return ResponseEntity.ok(ofSucceeded(fileUrl));

        } catch (Exception e) {
            log.error("Failed to upload file", e);
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, "Failed to upload file: " + e.getMessage()));
        }
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<BaseResponse<List<String>>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestAttribute("userId") UUID userId) throws IOException {
        List<String> fileUrls = new ArrayList<>();

        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                // Validate file type
                String contentType = file.getContentType();
                if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
                    log.warn("Skipping invalid file type: {}", contentType);
                    continue;
                }

                byte[] imageBytes = file.getBytes();
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : ".jpg";

                // Try to compress image
                try {
                    imageBytes = compressImage(imageBytes, originalFilename);
                } catch (Exception e) {
                    log.warn("Image compression failed for {}: {}", originalFilename, e.getMessage());
                    if (imageBytes.length > MAX_FILE_SIZE_BYTES) {
                        log.warn("Skipping file exceeding 2MB: {}", originalFilename);
                        continue;
                    }
                }

                String fileName = "expenses/" + userId + "/" + UUID.randomUUID() + extension;

                String fileUrl = storageService.uploadFile(
                        fileName,
                        new ByteArrayInputStream(imageBytes),
                        contentType,
                        (long) imageBytes.length
                );

                fileUrls.add(fileUrl);
            }
        }

        log.info("Uploaded {} files successfully", fileUrls.size());
        return ResponseEntity.ok(ofSucceeded(fileUrls));
    }

    private byte[] compressImage(byte[] imageBytes, String filename) throws Exception {
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

            String url = imgpressUrl + "/compress/one?format=webp&quality=40&width=1600&autoOrient=true";
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
        } catch (Exception e) {
            log.error("Failed to compress image via imgpress", e);
            throw e;
        }
    }
}
