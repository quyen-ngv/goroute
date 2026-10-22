package com.ds.goroute.controller;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.SocialSearchResponse;
import com.ds.goroute.service.SocialSearchService;
import com.ds.goroute.type.SocialSearchType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** The Feed search (epic 12 §4.3): people, public check-ins and public trips. Signed-in only. */
@RestController
@Validated
@RequestMapping("/v1/api/search")
@RequiredArgsConstructor
public class SocialSearchController extends BaseController {

    static final int MAX_PAGE_SIZE = 30;
    static final int MAX_PAGE = 100;

    private final SocialSearchService searchService;

    /**
     * {@code type} ALL (default) lists check-ins and trips mixed by relevance, with the best few
     * people on page 0; USER, CHECKIN and TRIP list that kind only.
     */
    @GetMapping
    public ResponseEntity<BaseResponse<SocialSearchResponse>> search(
            @RequestParam("q") @Size(max = 200) String query,
            @RequestParam(defaultValue = "ALL") SocialSearchType type,
            @RequestParam(defaultValue = "0") @Min(0) @Max(MAX_PAGE) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size,
            @CurrentUser UUID userId) {
        return ResponseEntity.ok(ofSucceeded(searchService.search(userId, query, type, page, size)));
    }
}
