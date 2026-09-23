package com.ds.goroute.partneronboarding;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.partneronboarding.domain.DraftStatus;
import com.ds.goroute.partneronboarding.dto.CreateDraftRequest;
import com.ds.goroute.partneronboarding.dto.DraftResponse;
import com.ds.goroute.partneronboarding.dto.DraftSummaryResponse;
import com.ds.goroute.partneronboarding.dto.OnboardingMeResponse;
import com.ds.goroute.partneronboarding.dto.SaveStepRequest;
import com.ds.goroute.partneronboarding.dto.SubmitDraftRequest;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.service.ImageUploadOutcome;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The listing wizard.
 *
 * <p>Authenticated but deliberately not behind {@code ROLE_PARTNER}: the first thing the
 * wizard does is turn an ordinary account into a partner, so demanding the role to reach it
 * would be a door that can only be opened from the inside.
 */
@Validated
@RestController
@RequestMapping("/v1/api/partner-onboarding")
@RequiredArgsConstructor
public class PartnerOnboardingController {

    private final PartnerOnboardingService onboardingService;

    @GetMapping("/me")
    public ResponseEntity<BaseResponse<OnboardingMeResponse>> me(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(onboardingService.me(userId)));
    }

    @PostMapping("/drafts")
    public ResponseEntity<BaseResponse<DraftResponse>> create(
            @CurrentUser UUID userId,
            @Valid @RequestBody CreateDraftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(onboardingService.create(userId, request)));
    }

    @GetMapping("/drafts")
    public ResponseEntity<BaseResponse<PageResponse<DraftSummaryResponse>>> list(
            @CurrentUser UUID userId,
            @RequestParam(required = false) DraftStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(onboardingService.list(userId, status, page, size)));
    }

    @GetMapping("/drafts/{draftId}")
    public ResponseEntity<BaseResponse<DraftResponse>> get(
            @CurrentUser UUID userId,
            @PathVariable UUID draftId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(onboardingService.get(userId, draftId)));
    }

    @PutMapping("/drafts/{draftId}/steps/{stepCode}")
    public ResponseEntity<BaseResponse<DraftResponse>> saveStep(
            @CurrentUser UUID userId,
            @PathVariable UUID draftId,
            @PathVariable @NotBlank @Size(max = 60) String stepCode,
            @Valid @RequestBody SaveStepRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                onboardingService.saveStep(userId, draftId, stepCode, request)));
    }

    /**
     * Photos are uploaded as they are chosen rather than held until submit: a phone that loses
     * the app mid-wizard should not lose the pictures too, and the draft only stores their URLs.
     */
    @PostMapping(value = "/drafts/{draftId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<BaseResponse<List<ImageUploadOutcome>>> uploadMedia(
            @CurrentUser UUID userId,
            @PathVariable UUID draftId,
            @RequestPart("files") List<MultipartFile> files) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(onboardingService.uploadMedia(userId, draftId, files)));
    }

    @PostMapping("/drafts/{draftId}/submit")
    public ResponseEntity<BaseResponse<SubmitResultResponse>> submit(
            @CurrentUser UUID userId,
            @PathVariable UUID draftId,
            @Valid @RequestBody(required = false) SubmitDraftRequest request) {
        Long expectedVersion = request == null ? null : request.getExpectedVersion();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(onboardingService.submit(userId, draftId, expectedVersion)));
    }

    @DeleteMapping("/drafts/{draftId}")
    public ResponseEntity<BaseResponse<Void>> abandon(
            @CurrentUser UUID userId,
            @PathVariable UUID draftId,
            @RequestParam(required = false) Long expectedVersion) {
        onboardingService.abandon(userId, draftId, expectedVersion);
        return ResponseEntity.noContent().build();
    }
}
