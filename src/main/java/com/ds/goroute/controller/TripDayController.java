package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.InsertTripDaysRequest;
import com.ds.goroute.dto.request.MoveTripDayRequest;
import com.ds.goroute.dto.request.RescheduleTripRequest;
import com.ds.goroute.service.trip.TripDayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Changes to a trip's days (epic 02): remove, insert or move a day, or give the trip new dates.
 * Editors only. The client reloads the trip and its activities afterwards.
 */
@RestController
@Validated
@RequestMapping("/v1/api/trips/{tripId}")
@RequiredArgsConstructor
public class TripDayController extends BaseController {

    private final TripDayService tripDayService;

    /** Removes day {@code dayNumber}, or days {@code dayNumber}..{@code toDay} (a destination's days). */
    @DeleteMapping("/days/{dayNumber}")
    public ResponseEntity<BaseResponse<Void>> removeDay(
            @PathVariable UUID tripId,
            @PathVariable @Min(1) @Max(400) int dayNumber,
            @RequestParam(required = false) @Min(1) @Max(400) Integer toDay,
            @CurrentUser UUID userId) {
        tripDayService.removeDays(tripId, dayNumber, toDay == null ? dayNumber : toDay, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/days")
    public ResponseEntity<BaseResponse<Void>> insertDays(
            @PathVariable UUID tripId,
            @Valid @RequestBody InsertTripDaysRequest request,
            @CurrentUser UUID userId) {
        int count = request.getCount() == null ? 1 : request.getCount();
        tripDayService.insertDays(tripId, request.getBeforeDay(), count, userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PostMapping("/days/{dayNumber}/move")
    public ResponseEntity<BaseResponse<Void>> moveDay(
            @PathVariable UUID tripId,
            @PathVariable @Min(1) @Max(400) int dayNumber,
            @Valid @RequestBody MoveTripDayRequest request,
            @CurrentUser UUID userId) {
        tripDayService.moveDay(tripId, dayNumber, request.getToDay(), userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }

    @PutMapping("/schedule")
    public ResponseEntity<BaseResponse<Void>> reschedule(
            @PathVariable UUID tripId,
            @Valid @RequestBody RescheduleTripRequest request,
            @CurrentUser UUID userId) {
        tripDayService.reschedule(tripId, request.getStartDate(), request.getEndDate(), request.isKeepDates(),
                request.getMergeInto(), userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
