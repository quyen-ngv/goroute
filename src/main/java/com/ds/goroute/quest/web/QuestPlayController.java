package com.ds.goroute.quest.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.quest.dto.QuestAnswerRequest;
import com.ds.goroute.quest.dto.QuestAnswerResponse;
import com.ds.goroute.quest.dto.QuestArrivalResponse;
import com.ds.goroute.quest.dto.QuestEntitlementResponse;
import com.ds.goroute.quest.dto.QuestRunResponse;
import com.ds.goroute.quest.dto.QuestSampleRequest;
import com.ds.goroute.quest.dto.QuestTipRequest;
import com.ds.goroute.quest.service.QuestEconomyService;
import com.ds.goroute.quest.service.QuestPlayService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Playing a quest (§6.4). The server decides arrival and grades answers; these endpoints never
 * return an answer, a correct-choice flag, or a checkpoint's coordinates beyond the current one.
 */
@RestController
@RequiredArgsConstructor
public class QuestPlayController {

    private final QuestPlayService playService;
    private final QuestEconomyService economyService;

    @PostMapping("/v1/api/quests/{questId}/runs")
    public ResponseEntity<BaseResponse<QuestRunResponse>> start(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID tripId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.ofSucceeded(playService.startRun(userId, questId, tripId)));
    }

    /** Join an existing run as a group member (§3.13). */
    @PostMapping("/v1/api/quest-runs/{runId}/join")
    public ResponseEntity<BaseResponse<QuestRunResponse>> join(
            @CurrentUser UUID userId,
            @PathVariable UUID runId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.joinRun(userId, runId)));
    }

    @GetMapping("/v1/api/quest-runs/{runId}")
    public ResponseEntity<BaseResponse<QuestRunResponse>> get(
            @CurrentUser UUID userId,
            @PathVariable UUID runId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.getRun(userId, runId)));
    }

    @PostMapping("/v1/api/quest-runs/{runId}/samples")
    public ResponseEntity<BaseResponse<QuestArrivalResponse>> sample(
            @CurrentUser UUID userId,
            @PathVariable UUID runId,
            @RequestBody QuestSampleRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.submitSample(userId, runId, request)));
    }

    @PostMapping("/v1/api/quest-runs/{runId}/questions/{questionId}/answer")
    public ResponseEntity<BaseResponse<QuestAnswerResponse>> answer(
            @CurrentUser UUID userId,
            @PathVariable UUID runId,
            @PathVariable UUID questionId,
            @RequestBody QuestAnswerRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                playService.answerQuestion(userId, runId, questionId, request)));
    }

    @PostMapping("/v1/api/quest-runs/{runId}/complete")
    public ResponseEntity<BaseResponse<QuestRunResponse>> complete(
            @CurrentUser UUID userId,
            @PathVariable UUID runId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.complete(userId, runId)));
    }

    @PostMapping("/v1/api/quests/{questId}/unlock")
    public ResponseEntity<BaseResponse<QuestEntitlementResponse>> unlock(
            @CurrentUser UUID userId,
            @PathVariable UUID questId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(economyService.unlock(userId, questId)));
    }

    @PostMapping("/v1/api/quests/{questId}/tip")
    public ResponseEntity<BaseResponse<Void>> tip(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @RequestBody QuestTipRequest request) {
        economyService.tip(userId, questId, request);
        return ResponseEntity.ok(BaseResponse.ofSucceeded(null));
    }
}
