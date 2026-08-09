package com.ds.goroute.controller;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.StorageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/partner/activities")
@RequiredArgsConstructor
public class PartnerActivityCommerceController {
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");
    private static final long MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
    private static final int MAX_IMAGES_PER_UPLOAD = 20;

    private final ActivityCommerceService service;
    private final PartnerAuthorizationService authorization;
    private final StorageService storage;

    @GetMapping
    public ResponseEntity<BaseResponse<List<MarketplaceActivityResponse>>> list(Authentication authentication,
                                                                                  @RequestParam UUID organizationId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerList(user(authentication), organizationId)));
    }

    /**
     * Receives media only after the host confirms the create/edit action. The browser keeps selected files locally until then.
     */
    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<List<String>>> uploadMedia(
            Authentication authentication,
            @RequestParam UUID organizationId,
            @RequestParam("files") List<MultipartFile> files) throws IOException {
        UUID actor = user(authentication);
        authorization.requirePermission(organizationId, actor, "ACTIVITY_WRITE");
        validateMedia(files);

        List<String> urls = new ArrayList<>();
        for (MultipartFile file : files) {
            String contentType = file.getContentType().toLowerCase();
            byte[] bytes = file.getBytes();
            String objectKey = "marketplace/activities/" + organizationId + "/" + UUID.randomUUID() + extension(file.getOriginalFilename(), contentType);
            urls.add(storage.uploadFile(objectKey, new ByteArrayInputStream(bytes), contentType, bytes.length));
        }
        return ResponseEntity.ok(BaseResponse.ofSucceeded(urls));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> create(Authentication authentication,
                                                                              @Valid @RequestBody UpsertMarketplaceActivityRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreate(user(authentication), request)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BaseResponse<MarketplaceActivityResponse>> update(Authentication authentication,
                                                                              @PathVariable UUID id,
                                                                              @Valid @RequestBody UpsertMarketplaceActivityRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdate(user(authentication), id, request)));
    }

    @GetMapping("/{id}/packages")
    public ResponseEntity<BaseResponse<List<ActivityPackageResponse>>> packages(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerPackages(user(authentication), id)));
    }

    @PostMapping("/{id}/packages")
    public ResponseEntity<BaseResponse<ActivityPackageResponse>> createPackage(Authentication authentication, @PathVariable UUID id,
                                                                                @Valid @RequestBody UpsertActivityPackageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreatePackage(user(authentication), id, request)));
    }

    @PutMapping("/packages/{id}")
    public ResponseEntity<BaseResponse<ActivityPackageResponse>> updatePackage(Authentication authentication, @PathVariable UUID id,
                                                                                @Valid @RequestBody UpsertActivityPackageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdatePackage(user(authentication), id, request)));
    }

    @GetMapping("/packages/{id}/slots")
    public ResponseEntity<BaseResponse<List<ActivitySlotResponse>>> slots(Authentication authentication, @PathVariable UUID id,
                                                                           @RequestParam(required = false) LocalDateTime from) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerSlots(user(authentication), id, from)));
    }

    @PostMapping("/packages/{id}/slots")
    public ResponseEntity<BaseResponse<ActivitySlotResponse>> createSlot(Authentication authentication, @PathVariable UUID id,
                                                                          @Valid @RequestBody UpsertActivitySlotRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BaseResponse.ofSucceeded(service.partnerCreateSlot(user(authentication), id, request)));
    }

    @PutMapping("/slots/{id}")
    public ResponseEntity<BaseResponse<ActivitySlotResponse>> updateSlot(Authentication authentication, @PathVariable UUID id,
                                                                          @Valid @RequestBody UpsertActivitySlotRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerUpdateSlot(user(authentication), id, request)));
    }

    @GetMapping("/orders")
    public ResponseEntity<BaseResponse<List<ActivityOrderResponse>>> orders(Authentication authentication, @RequestParam UUID organizationId,
                                                                            @RequestParam(required = false) String status,
                                                                            @RequestParam(defaultValue = "0") int page,
                                                                            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerOrders(user(authentication), organizationId, status, page, size)));
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> order(Authentication authentication, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerOrder(user(authentication), id)));
    }

    @PatchMapping("/orders/{id}/status")
    public ResponseEntity<BaseResponse<ActivityOrderResponse>> status(Authentication authentication, @PathVariable UUID id,
                                                                       @Valid @RequestBody UpdateActivityOrderStatusRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.partnerOrderStatus(user(authentication), id, request)));
    }

    private void validateMedia(List<MultipartFile> files) {
        if (files == null || files.isEmpty() || files.size() > MAX_IMAGES_PER_UPLOAD) {
            throw badRequest("Upload from 1 to " + MAX_IMAGES_PER_UPLOAD + " images at a time");
        }
        for (MultipartFile file : files) {
            String type = file == null ? null : file.getContentType();
            if (file == null || file.isEmpty()) throw badRequest("Image file is empty");
            if (file.getSize() > MAX_IMAGE_SIZE_BYTES) throw badRequest("Each image must be 5 MB or smaller");
            if (type == null || !ALLOWED_IMAGE_TYPES.contains(type.toLowerCase())) {
                throw badRequest("Only JPG, PNG, and WEBP images are accepted");
            }
        }
    }

    private String extension(String originalFilename, String contentType) {
        if (originalFilename != null) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot >= 0 && originalFilename.length() - dot <= 6) {
                String value = originalFilename.substring(dot).toLowerCase();
                if (value.matches("\\.(jpg|jpeg|png|webp)")) return value;
            }
        }
        return contentType.contains("png") ? ".png" : contentType.contains("webp") ? ".webp" : ".jpg";
    }

    private BusinessException badRequest(String message) {
        return new BusinessException(ErrorConstant.BAD_REQUEST, message, HttpStatus.BAD_REQUEST);
    }

    private UUID user(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        return principal instanceof UUID id ? id : UUID.fromString(principal.toString());
    }
}
