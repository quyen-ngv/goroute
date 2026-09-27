package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.ImageUploadBatchResponse;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/files")
@RequiredArgsConstructor
public class FileUploadController extends BaseController {

    private final FileUploadService fileUploadService;

    @PostMapping("/upload")
    public ResponseEntity<BaseResponse<String>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(fileUploadService.uploadImage(userId, file)));
    }

    /** A quest creator's voice recording (§3.14.3): m4a, aac or mp3, at most 10 MB. */
    @PostMapping("/upload-audio")
    public ResponseEntity<BaseResponse<String>> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(fileUploadService.uploadAudio(userId, file)));
    }

    /**
     * Each image reports its own outcome, so a photo rejected for its content does not
     * fail the rest of the batch and the app can show that one photo differently from a
     * network failure -- retrying a content rejection gives the same answer (MOD-04).
     */
    @PostMapping("/upload-multiple")
    public ResponseEntity<ImageUploadBatchResponse> uploadMultipleFiles(
            @RequestParam("files") List<MultipartFile> files,
            @CurrentUser UUID userId) {
        ImageUploadRequest request = ImageUploadRequest.of(
                userId, ImageUploadRequest.ImageEntryPoint.USER_UPLOAD, "expenses/" + userId);
        return ResponseEntity.ok(
                ImageUploadBatchResponse.fromOutcomes(fileUploadService.uploadImages(request, files)));
    }
}
