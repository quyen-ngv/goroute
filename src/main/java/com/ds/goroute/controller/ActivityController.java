package com.ds.goroute.controller;

import com.ds.goroute.dto.request.CreateActivityRequest;
import com.ds.goroute.dto.request.ReorderActivitiesRequest;
import com.ds.goroute.dto.request.UpdateActivityRequest;
import com.ds.goroute.dto.response.ActivityResponse;
import com.ds.goroute.service.ActivityService;
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
@RequestMapping("/v1/api/trips/{tripId}/activities")
@RequiredArgsConstructor
@Slf4j
public class ActivityController extends BaseService {
    
    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<BaseResponse<List<ActivityResponse>>> getActivities(
            @PathVariable UUID tripId,
            @RequestParam(required = false) Integer day,
            @RequestAttribute("userId") UUID userId) {
        List<ActivityResponse> activities = activityService.getActivities(tripId, day);
        return ResponseEntity.ok(ofSucceeded(activities));
    }

    @PostMapping
    public ResponseEntity<BaseResponse<ActivityResponse>> createActivity(
            @PathVariable UUID tripId,
            @Valid @RequestBody CreateActivityRequest request,
            @RequestAttribute("userId") UUID userId) {
        ActivityResponse activity = activityService.createActivity(tripId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(activity));
    }

    @PutMapping("/{activityId}")
    public ResponseEntity<BaseResponse<ActivityResponse>> updateActivity(
            @PathVariable UUID tripId,
            @PathVariable UUID activityId,
            @Valid @RequestBody UpdateActivityRequest request,
            @RequestAttribute("userId") UUID userId) {
        ActivityResponse activity = activityService.updateActivity(tripId, activityId, request, userId);
        return ResponseEntity.ok(ofSucceeded(activity));
    }

    @DeleteMapping("/{activityId}")
    public ResponseEntity<BaseResponse<Void>> deleteActivity(
            @PathVariable UUID tripId,
            @PathVariable UUID activityId,
            @RequestAttribute("userId") UUID userId) {
        activityService.deleteActivity(tripId, activityId, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PutMapping("/reorder")
    public ResponseEntity<BaseResponse<Void>> reorderActivities(
            @PathVariable UUID tripId,
            @Valid @RequestBody ReorderActivitiesRequest request,
            @RequestAttribute("userId") UUID userId) {
        activityService.reorderActivities(tripId, request, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
