package com.ds.goroute.quest.web;

import com.ds.goroute.annotations.CurrentUser;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.quest.dto.QuestAnswerRequest;
import com.ds.goroute.quest.dto.QuestAnswerResponse;
import com.ds.goroute.quest.dto.QuestArrivalResponse;
import com.ds.goroute.quest.dto.QuestEntitlementResponse;
import com.ds.goroute.quest.dto.QuestLocalRunRequest;
import com.ds.goroute.quest.dto.QuestLocalRunResponse;
import com.ds.goroute.quest.dto.QuestPackResponse;
import com.ds.goroute.quest.dto.QuestProximityRequest;
import com.ds.goroute.quest.dto.QuestProximityResponse;
import com.ds.goroute.quest.dto.QuestRunResponse;
import com.ds.goroute.quest.dto.QuestSampleRequest;
import com.ds.goroute.quest.dto.QuestStopVisitRequest;
import com.ds.goroute.quest.dto.QuestStopVisitResponse;
import com.ds.goroute.quest.dto.QuestTipRequest;
import com.ds.goroute.quest.service.QuestEconomyService;
import com.ds.goroute.quest.service.QuestPlayService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Playing a quest (§6.4). The run endpoints decide arrival and grade answers on the server and never
 * return an answer, a correct-choice flag, or a checkpoint's coordinates beyond the current one.
 *
 * <p>Local-first play is the exception, by design: {@code /pack} hands an entitled player the whole
 * quest to play on the phone, and {@code /local-runs} takes the finished run back and replays it.
 */
@RestController
@Validated
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

    /** The whole quest, answers included, for playing on the phone. Entitled players only. */
    @GetMapping("/v1/api/quests/{questId}/pack")
    public ResponseEntity<BaseResponse<QuestPackResponse>> pack(
            @CurrentUser UUID userId,
            @PathVariable UUID questId) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.pack(userId, questId)));
    }

    /** A run played on the phone, uploaded to be replayed and rewarded. Idempotent per client run id. */
    @PostMapping("/v1/api/quests/{questId}/local-runs")
    public ResponseEntity<BaseResponse<QuestLocalRunResponse>> syncLocalRun(
            @CurrentUser UUID userId,
            @PathVariable UUID questId,
            @Valid @RequestBody QuestLocalRunRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.syncLocalRun(userId, questId, request)));
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

    /** Buy one tier of the current AREA checkpoint's finding clues (§3.14.1). Idempotent. */
    @PostMapping("/v1/api/quest-runs/{runId}/checkpoints/{checkpointId}/clues/{tier}/buy")
    public ResponseEntity<BaseResponse<QuestRunResponse>> buyClue(
            @CurrentUser UUID userId,
            @PathVariable UUID runId,
            @PathVariable UUID checkpointId,
            @PathVariable @Min(1) @Max(3) int tier) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.buyClue(userId, runId, checkpointId, tier)));
    }

    /** Hot/cold: a band and a trend against the real spot, never a distance (§3.14.1). */
    @PostMapping("/v1/api/quest-runs/{runId}/checkpoints/{checkpointId}/proximity")
    public ResponseEntity<BaseResponse<QuestProximityResponse>> proximity(
            @CurrentUser UUID userId,
            @PathVariable UUID runId,
            @PathVariable UUID checkpointId,
            @Valid @RequestBody QuestProximityRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(
                playService.proximity(userId, runId, checkpointId, request)));
    }

    /** A storytelling point was heard (§3.14.2). Idempotent; only a GPS visit inside it counts. */
    @PostMapping("/v1/api/quest-runs/{runId}/stops/{stopId}/visit")
    public ResponseEntity<BaseResponse<QuestStopVisitResponse>> visitStop(
            @CurrentUser UUID userId,
            @PathVariable UUID runId,
            @PathVariable UUID stopId,
            @RequestBody(required = false) QuestStopVisitRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(playService.visitStop(userId, runId, stopId, request)));
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
