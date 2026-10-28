package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AssistantProfileRequest;
import com.ds.goroute.dto.request.AssistantProposalApplyRequest;
import com.ds.goroute.dto.request.AssistantThreadTitleRequest;
import com.ds.goroute.dto.response.AssistantMeResponse;
import com.ds.goroute.dto.response.AssistantProfileResponse;
import com.ds.goroute.dto.response.AssistantProposalResponse;
import com.ds.goroute.dto.response.AssistantThreadResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.service.assistant.AssistantAccessService;
import com.ds.goroute.service.assistant.AssistantConversationService;
import com.ds.goroute.service.assistant.AssistantProfileService;
import com.ds.goroute.service.assistant.AssistantProposalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pengoo, the chat assistant, from the app's side. Questions themselves are ordinary chat
 * messages (a mention in a trip chat, anything in a 1:1 thread); these endpoints cover the rest.
 * Thread endpoints act on the caller's own threads only: another person's or a non-Pengoo id is 404.
 */
@Validated
@RestController
@RequestMapping("/v1/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    static final int MAX_THREAD_PAGE_SIZE = 50;

    private final AssistantAccessService accessService;
    private final AssistantConversationService conversationService;
    private final AssistantProposalService proposalService;
    private final AssistantProfileService profileService;

    /** Whether the caller may ask Pengoo, and today's allowance. */
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<AssistantMeResponse>> me(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(accessService.me(userId)));
    }

    /**
     * The 1:1 thread to open, created when the caller has none. 403 while Pengoo is not available
     * to them. With {@code resumeWithinHours}: the latest thread they wrote in only if it was
     * active that recently, else an unused (empty) thread.
     */
    @GetMapping("/conversation")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> conversation(
            @CurrentUser UUID userId,
            @RequestParam(required = false) @Min(1) @Max(168) Integer resumeWithinHours) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(conversationService.open(userId, resumeWithinHours)));
    }

    /** The caller's threads they wrote in, latest first; {@code q} matches the shown title. */
    @GetMapping("/conversations")
    public ResponseEntity<BaseResponse<PageResponse<AssistantThreadResponse>>> conversations(
            @CurrentUser UUID userId,
            @RequestParam(required = false) @Size(max = 80) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_THREAD_PAGE_SIZE) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(conversationService.listThreads(userId, q, page, size)));
    }

    /** "New chat": the caller's unused thread when there is one, otherwise a new empty thread. */
    @PostMapping("/conversations")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> createConversation(@CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(conversationService.createThread(userId)));
    }

    @PatchMapping("/conversations/{conversationId}")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> renameConversation(
            @CurrentUser UUID userId, @PathVariable UUID conversationId,
            @Valid @RequestBody AssistantThreadTitleRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                conversationService.rename(userId, conversationId, request.getTitle())));
    }

    /** Deletes (archives) one of the caller's threads. */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<Void> deleteConversation(@CurrentUser UUID userId, @PathVariable UUID conversationId) {
        conversationService.archive(userId, conversationId);
        return ResponseEntity.noContent().build();
    }

    /** What Pengoo keeps about the caller across their 1:1 threads. */
    @GetMapping("/profile")
    public ResponseEntity<BaseResponse<AssistantProfileResponse>> profile(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(profileService.get(userId)));
    }

    /** Replaces that list; an empty one forgets it. */
    @PutMapping("/profile")
    public ResponseEntity<BaseResponse<AssistantProfileResponse>> replaceProfile(
            @CurrentUser UUID userId, @Valid @RequestBody AssistantProfileRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(profileService.replace(userId, request.getItems())));
    }

    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<BaseResponse<AssistantProposalResponse>> proposal(
            @CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(proposalService.get(userId, proposalId)));
    }

    /**
     * Applies a proposal as the caller (owner or editor of the trip; for a planned new trip, the
     * person who asked for it, who becomes its owner). Idempotent. The body is optional: a planned
     * new trip without dates needs {@code startDate}.
     */
    @PostMapping("/proposals/{proposalId}/apply")
    public ResponseEntity<BaseResponse<AssistantProposalResponse>> apply(
            @CurrentUser UUID userId, @PathVariable UUID proposalId,
            @Valid @RequestBody(required = false) AssistantProposalApplyRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                proposalService.apply(userId, proposalId, request == null ? null : request.getStartDate())));
    }

    @PostMapping("/proposals/{proposalId}/dismiss")
    public ResponseEntity<BaseResponse<AssistantProposalResponse>> dismiss(
            @CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(proposalService.dismiss(userId, proposalId)));
    }
}
