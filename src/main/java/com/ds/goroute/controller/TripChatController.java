package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.MarketplaceConversationResponse;
import com.ds.goroute.service.TripChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The one door into a trip's group chat.
 *
 * <p>Separate from the chat controller because the question it answers is a trip question --
 * are you in this trip -- and answering it here keeps the chat endpoints from having to know
 * what a trip is. Everything after opening happens through the ordinary chat endpoints.
 */
@RestController
@RequestMapping("/v1/api/trips/{tripId}")
@RequiredArgsConstructor
public class TripChatController {

    private final TripChatService tripChatService;

    /**
     * The trip's conversation, created on first use for trips that predate the feature.
     *
     * <p>A GET that may create looks odd and is deliberate: there is exactly one conversation
     * per trip and the client never chooses to make one, so asking for it and making sure it
     * exists are the same action.
     */
    @GetMapping("/conversation")
    public ResponseEntity<BaseResponse<MarketplaceConversationResponse>> conversation(
            @CurrentUser UUID userId, @PathVariable UUID tripId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(tripChatService.open(userId, tripId)));
    }
}
