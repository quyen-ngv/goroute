package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileUploadController extends BaseService {

    private final StorageService storageService;

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") UUID userId) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(ofFailed(400, "File is empty"));
            }

            // Generate unique filename
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename != null && originalFilename.contains(".")
                    ? originalFilename.substring(originalFilename.lastIndexOf("."))
                    : "";
            String fileName = "expenses/" + userId + "/" + UUID.randomUUID() + extension;

            // Upload to S3
            String fileUrl = storageService.uploadFile(
                    fileName,
                    file.getInputStream(),
                    file.getContentType(),
                    file.getSize()
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
                String originalFilename = file.getOriginalFilename();
                String extension = originalFilename != null && originalFilename.contains(".")
                        ? originalFilename.substring(originalFilename.lastIndexOf("."))
                        : "";
                String fileName = "expenses/" + userId + "/" + UUID.randomUUID() + extension;

                String fileUrl = storageService.uploadFile(
                        fileName,
                        file.getInputStream(),
                        file.getContentType(),
                        file.getSize()
                );

                fileUrls.add(fileUrl);
            }
        }

        log.info("Uploaded {} files successfully", fileUrls.size());
        return ResponseEntity.ok(ofSucceeded(fileUrls));
    }
}
