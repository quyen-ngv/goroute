package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.dto.response.MarketplaceMessageResponse;
import com.ds.goroute.dto.request.SendMarketplaceMessageRequest;
import com.ds.goroute.dto.request.UpdateConversationStatusRequest;
import com.ds.goroute.service.MarketplaceChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/marketplace-conversations")
@RequiredArgsConstructor
public class AdminMarketplaceChatController {
    private final MarketplaceChatService service;

    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-conversations','get')")
    public ResponseEntity<BaseResponse<List<MarketplaceConversationResponse>>> list(
            @RequestParam(required=false) String q,@RequestParam(required=false) String search,
            @RequestParam(required=false) String status,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="50") int size){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminList(q!=null?q:search,status,page,size)));
    }

    @GetMapping("/{id}/messages")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-conversations','get')")
    public ResponseEntity<BaseResponse<List<MarketplaceMessageResponse>>> messages(@PathVariable UUID id,
            @RequestParam(required=false) Long afterSequence,@RequestParam(defaultValue="100") int limit){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminMessages(id,afterSequence,limit)));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-conversations','update')")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> update(Authentication authentication,
            @PathVariable UUID id,@Valid @RequestBody UpdateConversationStatusRequest request){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminUpdate(UUID.fromString(authentication.getName()),id,request)));
    }

    @PostMapping("/{id}/messages")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-conversations','create')")
    public ResponseEntity<BaseResponse<MarketplaceMessageResponse>> send(Authentication authentication,@PathVariable UUID id,
            @Valid @RequestBody SendMarketplaceMessageRequest request){
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.adminSend(UUID.fromString(authentication.getName()),id,request)));
    }

    @DeleteMapping("/{id}/messages/{messageId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'marketplace-conversations','delete')")
    public ResponseEntity<BaseResponse<Void>> redact(Authentication authentication,@PathVariable UUID id,
            @PathVariable UUID messageId,@RequestParam(required=false)String reason){
        service.adminRedact(UUID.fromString(authentication.getName()),id,messageId,reason);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
