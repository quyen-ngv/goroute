package com.ds.goroute.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface FileUploadService {
    String uploadImage(UUID userId, MultipartFile file);

    List<String> uploadImages(UUID userId, List<MultipartFile> files);

    String uploadVideo(UUID userId, MultipartFile file);
}
