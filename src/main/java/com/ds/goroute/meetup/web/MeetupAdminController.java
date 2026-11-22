package com.ds.goroute.meetup.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.dto.AdminMeetupDetail;
import com.ds.goroute.meetup.dto.AdminMeetupRow;
import com.ds.goroute.meetup.dto.MeetupApproveRequest;
import com.ds.goroute.meetup.dto.MeetupRejectRequest;
import com.ds.goroute.meetup.dto.MeetupRemoveRequest;
import com.ds.goroute.meetup.service.MeetupAdminService;
import com.ds.goroute.type.MeetupAdminSort;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Console review and management of meetups (SPEC_V2 §2.5). ROLE_ADMIN by the URL rule plus
 * {@code meetups:get|review|update} per endpoint.
 */
@Validated
@RestController
@RequestMapping("/v1/api/admin/meetups")
@RequiredArgsConstructor
public class MeetupAdminController {

    private final MeetupAdminService adminService;

    /**
     * {@code status} and {@code category} accept comma lists. Without {@code sort}, a list of only
     * PENDING_REVIEW is ordered by deadline (soonest to leave the map first), anything else by
     * submission time, newest first.
     */
    @GetMapping
    @PreAuthorize("@adminAuthorization.can(authentication,'meetups','get')")
    public ResponseEntity<BaseResponse<PageResponse<AdminMeetupRow>>> list(
            @RequestParam(required = false) List<MeetupStatus> status,
            @RequestParam(required = false) List<MeetupCategory> category,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "false") boolean hidePast,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        MeetupAdminSort parsedSort;
        try {
            parsedSort = MeetupAdminSort.fromWire(sort);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "sort must be deadline or submittedAt");
        }
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.list(status, category, search, provinceCode,
                dateFrom, dateTo, hidePast, parsedSort, page, size)));
    }

    @GetMapping("/{meetupId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'meetups','get')")
    public ResponseEntity<BaseResponse<AdminMeetupDetail>> detail(@PathVariable UUID meetupId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.detail(meetupId)));
    }

    @PostMapping("/{meetupId}/approve")
    @PreAuthorize("@adminAuthorization.can(authentication,'meetups','review')")
    public ResponseEntity<BaseResponse<AdminMeetupDetail>> approve(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody MeetupApproveRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                adminService.approve(userId, meetupId, request.getExpectedVersion())));
    }

    @PostMapping("/{meetupId}/reject")
    @PreAuthorize("@adminAuthorization.can(authentication,'meetups','review')")
    public ResponseEntity<BaseResponse<AdminMeetupDetail>> reject(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody MeetupRejectRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.reject(userId, meetupId, request)));
    }

    @PostMapping("/{meetupId}/remove")
    @PreAuthorize("@adminAuthorization.can(authentication,'meetups','update')")
    public ResponseEntity<BaseResponse<AdminMeetupDetail>> remove(
            @CurrentUser UUID userId,
            @PathVariable UUID meetupId,
            @Valid @RequestBody MeetupRemoveRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(adminService.remove(userId, meetupId, request.getReason())));
    }
}
