package com.ds.goroute.quest.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.config.AdminAuthorization;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.quest.domain.QuestReviewComment;
import com.ds.goroute.quest.domain.QuestReviewDecisionRow;
import com.ds.goroute.quest.dto.CreateQuestRequest;
import com.ds.goroute.quest.dto.QuestDraftResponse;
import com.ds.goroute.quest.dto.QuestReviewDecisionRequest;
import com.ds.goroute.quest.dto.QuestReviewQueueItem;
import com.ds.goroute.quest.dto.QuestReviewResultResponse;
import com.ds.goroute.quest.dto.QuestSummaryResponse;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;
import com.ds.goroute.quest.service.QuestBuilderService;
import com.ds.goroute.quest.service.QuestReviewService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The console side of the dual builder (D15), for the content team building SYSTEM quests. Unlike
 * {@link QuestBuilderController} this may mint {@code origin = SYSTEM}, so it is gated on
 * {@code quests:update}. It reads and writes the same draft rows as the app builder — the two are
 * one flow, not two.
 */
@Validated
@RestController
@RequestMapping("/v1/api/admin/quests")
@RequiredArgsConstructor
public class QuestAdminController {

    private final QuestBuilderService builderService;
    private final QuestReviewService reviewService;
    private final AdminAuthorization adminAuthorization;

    @PostMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','update')")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> create(
            @CurrentUser UUID userId,
            @RequestBody(required = false) CreateQuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(builderService.create(userId, request, true)));
    }

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','get')")
    public ResponseEntity<BaseResponse<PageResponse<QuestSummaryResponse>>> listMine(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.listMine(userId, status, page, size)));
    }

    @GetMapping("/{questId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','get')")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> get(
            @PathVariable UUID questId,
            @CurrentUser UUID userId) {
        // Admin view: a reviewer may read any quest's draft, not only their own.
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.getDraft(questId, userId, true)));
    }

    @PutMapping("/{questId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','update')")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> save(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @RequestBody SaveQuestDraftRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.saveDraft(questId, userId, request)));
    }

    @PostMapping("/{questId}/submit")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','update')")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> submit(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @RequestParam(required = false) Long expectedVersion) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.submit(questId, userId, expectedVersion, false)));
    }

    // --- review (§3.1, §3.3) -----------------------------------------------------------

    @GetMapping("/review-queue")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','review')")
    public ResponseEntity<BaseResponse<PageResponse<QuestReviewQueueItem>>> reviewQueue(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(reviewService.queue(page, size)));
    }

    @PostMapping("/{questId}/open-review")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','review')")
    public ResponseEntity<BaseResponse<QuestReviewResultResponse>> openReview(
            @PathVariable UUID questId,
            @RequestParam(required = false) Long expectedVersion) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(reviewService.openForReview(questId, expectedVersion)));
    }

    @PostMapping("/{questId}/decide")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','review')")
    public ResponseEntity<BaseResponse<QuestReviewResultResponse>> decide(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @RequestBody QuestReviewDecisionRequest request,
            Authentication authentication) {
        boolean superAdmin = adminAuthorization.isSuperAdmin(authentication);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                reviewService.decide(questId, userId, superAdmin, request)));
    }

    @GetMapping("/{questId}/review")
    @PreAuthorize("@adminAuthorization.can(authentication,'quests','review')")
    public ResponseEntity<BaseResponse<Map<String, Object>>> reviewHistory(@PathVariable UUID questId) {
        List<QuestReviewDecisionRow> decisions = reviewService.decisions(questId);
        List<QuestReviewComment> comments = reviewService.comments(questId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(Map.of("decisions", decisions, "comments", comments)));
    }

    /** Suspend or reactivate a creator (§3.2): a SUSPENDED creator's quests drop out of discovery. */
    @PostMapping("/creators/{creatorId}/status")
    @PreAuthorize("@adminAuthorization.can(authentication,'quest-creators','update')")
    public ResponseEntity<BaseResponse<Void>> setCreatorStatus(
            @PathVariable UUID creatorId,
            @RequestParam String status) {
        reviewService.setCreatorStatus(creatorId, status);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }
}
