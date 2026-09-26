package com.ds.goroute.quest.web;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.quest.dto.QuestPublicDetailResponse;
import com.ds.goroute.quest.dto.QuestPublicSummaryResponse;
import com.ds.goroute.quest.service.QuestDiscoveryService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Public quest discovery (§6.4). Read-only, unauthenticated-friendly, and gated by the shared
 * publicQuestGate. Returns only public shapes — no checkpoint coordinates, answers or hints.
 */
@Validated
@RestController
@RequestMapping("/v1/api/quests")
@RequiredArgsConstructor
public class QuestDiscoveryController {

    private final QuestDiscoveryService discoveryService;

    @GetMapping
    public ResponseEntity<BaseResponse<PageResponse<QuestPublicSummaryResponse>>> search(
            @RequestParam(required = false) String provinceCode,
            @RequestParam(required = false) String language,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(discoveryService.search(provinceCode, language, page, size)));
    }

    @GetMapping("/{questId}")
    public ResponseEntity<BaseResponse<QuestPublicDetailResponse>> detail(@PathVariable UUID questId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(discoveryService.detail(questId)));
    }
}
