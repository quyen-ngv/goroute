package com.ds.goroute.controller;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator media library.
 *
 * <p>Both upload paths go through the shared upload service (MOD-05). Administrator
 * images are trusted a little further -- the service applies a relaxed threshold for
 * {@code admin-*} entry points -- but they are not skipped, because an exemption that is
 * the result of forgetting is indistinguishable from an exemption that was decided.
 */
@RestController
@RequestMapping("/v1/api/admin/media")
@RequiredArgsConstructor
public class AdminMediaController {

    private final AdminMapper adminMapper;
    private final FileUploadService fileUploadService;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'media','get')")
    public BaseResponse<List<Map<String, Object>>> list(@RequestParam(defaultValue = "") String search) {
        return BaseResponse.ofSucceeded(adminMapper.findMedia(search));
    }

    @PostMapping("/upload")
    @PreAuthorize("@adminAuthorization.can(authentication,'media','create')")
    public BaseResponse<Map<String, String>> upload(@RequestParam MultipartFile file,
                                                    @RequestAttribute UUID userId) {
        ImageUploadOutcome outcome = fileUploadService.uploadImage(
                ImageUploadRequest.of(userId, ImageUploadRequest.ImageEntryPoint.ADMIN_MEDIA, "admin-media"),
                file);
        return save(require(outcome), userId, file.getOriginalFilename());
    }

    /**
     * Fetching an image from an arbitrary address is still a way of publishing an image,
     * so the downloaded bytes go through the same checks as an uploaded file.
     */
    @PostMapping("/from-url")
    @PreAuthorize("@adminAuthorization.can(authentication,'media','create')")
    public BaseResponse<Map<String, String>> fromUrl(@RequestParam String url,
                                                     @RequestParam(required = false) String caption,
                                                     @RequestAttribute UUID userId) {
        ImageUploadOutcome outcome = fileUploadService.uploadImageFromUrl(
                ImageUploadRequest.of(userId, ImageUploadRequest.ImageEntryPoint.ADMIN_MEDIA_FROM_URL,
                        "admin-media"),
                url);
        return save(require(outcome), userId, caption);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'media','delete')")
    public BaseResponse<Void> delete(@PathVariable UUID id) {
        adminMapper.softDeleteMedia(id);
        return BaseResponse.ofSucceeded(null);
    }

    private String require(ImageUploadOutcome outcome) {
        if (outcome.isAccepted()) {
            return outcome.url();
        }
        throw new BusinessException(
                outcome.isContentRejection()
                        ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION
                        : ErrorConstant.INVALID_PARAMETERS,
                outcome.failureMessage());
    }

    private BaseResponse<Map<String, String>> save(String url, UUID userId, String caption) {
        adminMapper.insertMedia(UUID.randomUUID(), url, caption, userId);
        return BaseResponse.ofSucceeded(Map.of("url", url));
    }
}
