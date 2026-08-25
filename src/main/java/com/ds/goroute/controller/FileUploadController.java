package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import com.ds.goroute.service.FileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/files")
@RequiredArgsConstructor
public class FileUploadController extends BaseService {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(fileUploadService.uploadImage(userId, file)));
    }

    @PostMapping("/upload-multiple")
    public ResponseEntity<BaseResponse<List<String>>> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @RequestAttribute("userId") UUID userId) {
        return ResponseEntity.ok(ofSucceeded(fileUploadService.uploadImages(userId, files)));
    }
}
