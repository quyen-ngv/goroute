package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.AssistantMeResponse;
import com.ds.goroute.dto.response.AssistantProposalResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.service.assistant.AssistantAccessService;
import com.ds.goroute.service.assistant.AssistantConversationService;
import com.ds.goroute.service.assistant.AssistantProposalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Pengoo, the chat assistant, from the app's side. Questions themselves are ordinary chat
 * messages (a mention in a trip chat, anything in the 1:1 chat); these endpoints cover the rest.
 */
@RestController
@RequestMapping("/v1/api/assistant")
@RequiredArgsConstructor
public class AssistantController {

    private final AssistantAccessService accessService;
    private final AssistantConversationService conversationService;
    private final AssistantProposalService proposalService;

    /** Whether the caller may ask Pengoo, and today's allowance. */
    @GetMapping("/me")
    public ResponseEntity<BaseResponse<AssistantMeResponse>> me(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(accessService.me(userId)));
    }

    /** The caller's 1:1 chat with Pengoo, created on first open. 403 while not available to them. */
    @GetMapping("/conversation")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> conversation(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(conversationService.open(userId)));
    }

    @GetMapping("/proposals/{proposalId}")
    public ResponseEntity<BaseResponse<AssistantProposalResponse>> proposal(
            @CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(proposalService.get(userId, proposalId)));
    }

    /** Applies a proposal as the caller (owner or editor of the trip). Idempotent. */
    @PostMapping("/proposals/{proposalId}/apply")
    public ResponseEntity<BaseResponse<AssistantProposalResponse>> apply(
            @CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(proposalService.apply(userId, proposalId)));
    }

    @PostMapping("/proposals/{proposalId}/dismiss")
    public ResponseEntity<BaseResponse<AssistantProposalResponse>> dismiss(
            @CurrentUser UUID userId, @PathVariable UUID proposalId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(proposalService.dismiss(userId, proposalId)));
    }
}
