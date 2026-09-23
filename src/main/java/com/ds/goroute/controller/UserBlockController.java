package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.BlockUserRequest;
import com.ds.goroute.dto.response.UserBlockResponse;
import com.ds.goroute.service.UserBlockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Blocking, from the point of view of the person doing it.
 *
 * <p>Every route acts on the caller's own block list and takes the other person from the
 * path, so there is no way to ask about, or change, somebody else's.
 */
@RestController
@RequestMapping("/v1/api/users")
@RequiredArgsConstructor
public class UserBlockController {

    private final UserBlockService service;

    /** Everyone the caller has blocked. */
    @GetMapping("/blocks")
    public ResponseEntity<BaseResponse<List<UserBlockResponse>>> list(@CurrentUser UUID userId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.listBlocked(userId)));
    }

    /**
     * Blocks somebody. The body is optional: a reason helps the blocker remember, and
     * insisting on one is how people abandon the button they needed.
     */
    @PostMapping("/{targetUserId}/block")
    public ResponseEntity<BaseResponse<UserBlockResponse>> block(@CurrentUser UUID userId,
                                                                 @PathVariable UUID targetUserId,
                                                                 @Valid @RequestBody(required = false) BlockUserRequest request) {
        String reason = request == null ? null : request.getReason();
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.block(userId, targetUserId, reason)));
    }

    @DeleteMapping("/{targetUserId}/block")
    public ResponseEntity<BaseResponse<Void>> unblock(@CurrentUser UUID userId,
                                                      @PathVariable UUID targetUserId) {
        service.unblock(userId, targetUserId);
        return ResponseEntity.ok(BaseResponse.ofSucceeded());
    }
}
