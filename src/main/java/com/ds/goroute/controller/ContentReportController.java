package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.ModerationPreviewRequest;
import com.ds.goroute.dto.request.ReportContentRequest;
import com.ds.goroute.dto.response.ContentReportResponse;
import com.ds.goroute.dto.response.ModerationPreviewResponse;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.ModerationAdminService;
import com.ds.goroute.type.ContentReportReason;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * The reporting path every public surface uses (SOC-06a).
 *
 * <p>One endpoint for every content kind. Content is addressed by type and id, so a new
 * kind of post is reportable the day it ships rather than after somebody remembers to add
 * another endpoint.
 */
@RestController
@RequestMapping("/v1/api")
@RequiredArgsConstructor
public class ContentReportController extends BaseController {

    private final ContentModerationService contentModerationService;
    private final ModerationAdminService moderationAdminService;

    @PostMapping("/reports")
    public ResponseEntity<BaseResponse<ContentReportResponse>> report(
            @Valid @RequestBody ReportContentRequest request,
            @CurrentUser UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(contentModerationService.report(userId, request)));
    }

    /** The short reason list the app shows; kept server-side so both stay in step. */
    @GetMapping("/reports/reasons")
    public ResponseEntity<BaseResponse<List<ContentReportReason>>> reasons() {
        return ResponseEntity.ok(ofSucceeded(Arrays.asList(ContentReportReason.values())));
    }

    /**
     * Lets the app warn the author while they type without shipping the term list to
     * every device. It is a hint only -- the decision that counts is taken again when the
     * content is submitted.
     */
    @PostMapping("/moderation/preview")
    public ResponseEntity<BaseResponse<ModerationPreviewResponse>> preview(
            @Valid @RequestBody ModerationPreviewRequest request) {
        return ResponseEntity.ok(ofSucceeded(moderationAdminService.preview(request)));
    }
}
