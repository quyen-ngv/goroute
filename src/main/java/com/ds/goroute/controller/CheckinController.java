package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CheckinRequest;
import com.ds.goroute.dto.response.CheckinResponse;
import com.ds.goroute.service.CheckinService;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.service.BaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/trips/{tripId}")
@RequiredArgsConstructor
@Slf4j
public class CheckinController extends BaseService {
    
    private final CheckinService checkinService;

    @PostMapping("/activities/{activityId}/checkin")
    public ResponseEntity<BaseResponse<CheckinResponse>> checkin(
            @PathVariable UUID tripId,
            @PathVariable UUID activityId,
            @Valid @RequestBody CheckinRequest request,
            @RequestAttribute("userId") UUID userId) {
        CheckinResponse checkin = checkinService.checkin(tripId, activityId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(checkin));
    }

    @GetMapping("/checkins")
    public ResponseEntity<BaseResponse<List<CheckinResponse>>> getCheckins(
            @PathVariable UUID tripId,
            @RequestParam(required = false) UUID activityId,
            @RequestAttribute("userId") UUID userId) {
        List<CheckinResponse> checkins = checkinService.getCheckins(tripId, activityId);
        return ResponseEntity.ok(ofSucceeded(checkins));
    }
}
