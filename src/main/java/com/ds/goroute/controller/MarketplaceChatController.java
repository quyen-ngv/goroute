package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.EditMarketplaceMessageRequest;
import com.ds.goroute.dto.request.MuteConversationRequest;
import com.ds.goroute.dto.request.PinMessageRequest;
import com.ds.goroute.dto.request.ReactToMessageRequest;
import com.ds.goroute.dto.request.SendMarketplaceMessageRequest;
import com.ds.goroute.dto.request.StartMarketplaceConversationRequest;
import com.ds.goroute.dto.response.ImageUploadBatchResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.MarketplaceChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a chat screen does, for every kind of conversation.
 *
 * <p>Trip groups are opened through their trip (see {@code TripChatController}), which is
 * where the membership question belongs; from that point on they are read, sent to and
 * muted through exactly these endpoints.
 */
@RestController
@RequestMapping("/v1/api/marketplace-chat")
@RequiredArgsConstructor
public class MarketplaceChatController {

    private static final int MAX_ATTACHMENTS = 10;

    private final MarketplaceChatService chatService;
    private final FileUploadService fileUploadService;

    @PostMapping("/conversations")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> start(
            @CurrentUser UUID userId, @Valid @RequestBody StartMarketplaceConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(chatService.start(userId, request)));
    }

    /** The inbox. {@code types} is the filter chips; leaving it out means everything. */
    @GetMapping("/conversations")
    public ResponseEntity<BaseResponse<List<MarketplaceConversationResponse>>> list(
            @CurrentUser UUID userId,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.listMine(userId, types, page, size)));
    }

    /** The number on the tab. */
    @GetMapping("/conversations/unread-count")
    public ResponseEntity<BaseResponse<Map<String, Long>>> unreadCount(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                Map.of("count", chatService.unreadConversationCount(userId))));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> get(
            @CurrentUser UUID userId, @PathVariable UUID id) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.get(userId, id)));
    }

    /** Catch-up: everything after a sequence number the client already has. */
    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<BaseResponse<List<MarketplaceMessageResponse>>> messages(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @RequestParam(required = false) Long afterSequence,
            @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.messages(userId, id, afterSequence, limit)));
    }

    /** Opening the thread: the newest page, and older pages behind {@code beforeSequence}. */
    @GetMapping("/conversations/{id}/messages/latest")
    public ResponseEntity<BaseResponse<List<MarketplaceMessageResponse>>> latestMessages(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @RequestParam(required = false) Long beforeSequence,
            @RequestParam(defaultValue = "40") int limit) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.latestMessages(userId, id, beforeSequence, limit)));
    }

    @GetMapping("/conversations/{id}/messages/search")
    public ResponseEntity<BaseResponse<List<MarketplaceMessageResponse>>> searchMessages(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @RequestParam String query,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.searchMessages(userId, id, query, limit)));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<BaseResponse<MarketplaceMessageResponse>> send(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody SendMarketplaceMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(chatService.send(userId, id, request)));
    }

    /**
     * Photos, uploaded before the message that carries them.
     *
     * <p>Two steps rather than one multipart send: the picture is checked and stored first,
     * so a rejected photo is one photo the sender can drop, and the message itself is still
     * the same idempotent call as any other.
     */
    @PostMapping("/conversations/{id}/attachments")
    public ResponseEntity<ImageUploadBatchResponse> uploadAttachments(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @RequestParam("files") List<MultipartFile> files) {
        chatService.get(userId, id);
        if (files == null || files.isEmpty()) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "At least one file is required");
        }
        if (files.size() > MAX_ATTACHMENTS) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST,
                    "At most " + MAX_ATTACHMENTS + " images can be sent at once");
        }
        ImageUploadRequest request = ImageUploadRequest.of(
                userId, ImageUploadRequest.ImageEntryPoint.CHAT_ATTACHMENT, "chat/" + id);
        return ResponseEntity.ok(ImageUploadBatchResponse.fromOutcomes(fileUploadService.uploadImages(request, files)));
    }

    @PostMapping("/conversations/{id}/read/{messageId}")
    public ResponseEntity<BaseResponse<Void>> read(
            @CurrentUser UUID userId, @PathVariable UUID id, @PathVariable UUID messageId) {
        chatService.markRead(userId, id, messageId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @DeleteMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<BaseResponse<Void>> deleteMessage(
            @CurrentUser UUID userId, @PathVariable UUID id, @PathVariable UUID messageId) {
        chatService.deleteOwnMessage(userId, id, messageId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }

    @PatchMapping("/conversations/{id}/messages/{messageId}")
    public ResponseEntity<BaseResponse<MarketplaceMessageResponse>> editMessage(
            @CurrentUser UUID userId, @PathVariable UUID id, @PathVariable UUID messageId,
            @Valid @RequestBody EditMarketplaceMessageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.editMessage(userId, id, messageId, request)));
    }

    @PostMapping("/conversations/{id}/messages/{messageId}/reactions")
    public ResponseEntity<BaseResponse<MarketplaceMessageResponse>> react(
            @CurrentUser UUID userId, @PathVariable UUID id, @PathVariable UUID messageId,
            @Valid @RequestBody ReactToMessageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                chatService.react(userId, id, messageId, allowedEmoji(request), true)));
    }

    @DeleteMapping("/conversations/{id}/messages/{messageId}/reactions")
    public ResponseEntity<BaseResponse<MarketplaceMessageResponse>> unreact(
            @CurrentUser UUID userId, @PathVariable UUID id, @PathVariable UUID messageId,
            @RequestParam String emoji) {
        ReactToMessageRequest request = new ReactToMessageRequest();
        request.setEmoji(emoji);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                chatService.react(userId, id, messageId, allowedEmoji(request), false)));
    }

    @PutMapping("/conversations/{id}/mute")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> mute(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @Valid @RequestBody MuteConversationRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(chatService.mute(userId, id, request)));
    }

    /** A null {@code messageId} clears the pin. */
    @PutMapping("/conversations/{id}/pin")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> pin(
            @CurrentUser UUID userId, @PathVariable UUID id,
            @RequestBody(required = false) PinMessageRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                chatService.pin(userId, id, request == null ? null : request.getMessageId())));
    }

    private String allowedEmoji(ReactToMessageRequest request) {
        if (!request.isAllowed()) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "That reaction is not available");
        }
        return request.getEmoji().trim();
    }
}
