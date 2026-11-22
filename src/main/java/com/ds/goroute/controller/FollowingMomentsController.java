package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.MarkFollowingMomentsReadRequest;
import com.ds.goroute.dto.response.FollowingMomentsResponse;
import com.ds.goroute.service.FollowingMomentsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The moments popup (epic 12): unread check-ins and public trips of the people the signed-in user
 * follows. Each one is a follower notification; reading a post here reads that notification.
 */
@RestController
@Validated
@RequestMapping("/v1/api/following/moments")
@RequiredArgsConstructor
public class FollowingMomentsController extends BaseController {

    private final FollowingMomentsService momentsService;

    @GetMapping
    public ResponseEntity<BaseResponse<FollowingMomentsResponse>> unread(@CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(momentsService.unread(userId)));
    }

    /** Returns how many notifications changed; posts already read or never announced count 0. */
    @PostMapping("/read")
    public ResponseEntity<BaseResponse<Integer>> markRead(
            @CurrentUser UUID userId,
            @Valid @RequestBody MarkFollowingMomentsReadRequest request) {
        return ResponseEntity.ok(ofSucceeded(momentsService.markRead(userId, request.getPosts())));
    }
}
