package com.ds.goroute.quest.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.quest.dto.CreateQuestRequest;
import com.ds.goroute.quest.dto.QuestDraftResponse;
import com.ds.goroute.quest.dto.QuestSummaryResponse;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;
import com.ds.goroute.quest.service.QuestBuilderService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The creator's builder (D15). Authenticated but not behind a creator role: the first thing a
 * creator does is become one, and §3.2 dropped the up-front gate on who may author. SYSTEM-origin
 * creation is not reachable here — that is an operator action on the console. Discovery lives
 * under {@code /v1/api/quests}; this path is only the draft workshop.
 */
@Validated
@RestController
@RequestMapping("/v1/api/quest-builder")
@RequiredArgsConstructor
public class QuestBuilderController {

    private final QuestBuilderService builderService;

    @PostMapping("/quests")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> create(
            @CurrentUser UUID userId,
            @RequestBody(required = false) CreateQuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(builderService.create(userId, request, false)));
    }

    @GetMapping("/quests")
    public ResponseEntity<BaseResponse<PageResponse<QuestSummaryResponse>>> listMine(
            @CurrentUser UUID userId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.listMine(userId, status, page, size)));
    }

    @GetMapping("/quests/{questId}")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> get(
            @CurrentUser UUID userId,
            @PathVariable UUID questId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.getDraft(questId, userId, false)));
    }

    @PutMapping("/quests/{questId}")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> save(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @RequestBody SaveQuestDraftRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.saveDraft(questId, userId, request)));
    }

    @PostMapping("/quests/{questId}/submit")
    public ResponseEntity<BaseResponse<QuestDraftResponse>> submit(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @RequestParam(required = false) Long expectedVersion) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(builderService.submit(questId, userId, expectedVersion, true)));
    }
}
