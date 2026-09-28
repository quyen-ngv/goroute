package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestArObject;
import com.ds.goroute.quest.domain.QuestArObjectAsset;
import com.ds.goroute.entity.StarTransaction;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCheckpointClue;
import com.ds.goroute.quest.domain.QuestCheckpointStop;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestEntitlement;
import com.ds.goroute.quest.domain.QuestLocationSample;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunClue;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunQuestion;
import com.ds.goroute.quest.domain.QuestRunStopVisit;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestAnswerRequest;
import com.ds.goroute.quest.dto.QuestAnswerResponse;
import com.ds.goroute.quest.dto.QuestArObjectView;
import com.ds.goroute.quest.dto.QuestArTapRequest;
import com.ds.goroute.quest.dto.QuestArTapResponse;
import com.ds.goroute.quest.dto.QuestArrivalResponse;
import com.ds.goroute.quest.dto.QuestLocalRunRequest;
import com.ds.goroute.quest.dto.QuestLocalRunResponse;
import com.ds.goroute.quest.dto.QuestPackResponse;
import com.ds.goroute.quest.dto.QuestProximityRequest;
import com.ds.goroute.quest.dto.QuestProximityResponse;
import com.ds.goroute.quest.dto.QuestRunResponse;
import com.ds.goroute.quest.dto.QuestSampleRequest;
import com.ds.goroute.quest.dto.QuestStopVisitRequest;
import com.ds.goroute.quest.dto.QuestStopVisitResponse;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.checkin.VerificationTarget;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestArBehavior;
import com.ds.goroute.type.QuestClueKind;
import com.ds.goroute.type.QuestCompletionMode;
import com.ds.goroute.type.QuestQuestionType;
import com.ds.goroute.type.QuestRunStatus;
import com.ds.goroute.type.QuestStatus;
import com.ds.goroute.utils.GeoDistance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Playing a quest (§3.8, §3.12). The server owns arrival and grading (rules 4, 2); the client only
 * learns arrived/not and right/wrong. A run pins the version it started on (D11), so this reads the
 * snapshot, never the live draft.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QuestPlayService {

    private final QuestRepository questRepository;
    private final QuestRunRepository runRepository;
    private final CheckinVerifier checkinVerifier;
    private final QuestAnswerGrader grader;
    private final BusinessConfigService config;
    private final StarService starService;
    private final PassportMapper passportMapper;
    private final MarketplaceJson json;
    private final QuestEconomyService economyService;

    /** Hot/cold bands (§3.14.1), in metres from the real spot. */
    static final int HOT_WITHIN_M = 25;
    static final int WARM_WITHIN_M = 60;
    static final int COOL_WITHIN_M = 150;
    /** A move smaller than this since the last question is SAME, not CLOSER/FARTHER. */
    static final double TREND_DEADBAND_M = 5;

    @Transactional
    public QuestRunResponse startRun(UUID userId, UUID questId, UUID tripId) {
        return startRun(userId, questId, tripId, false);
    }

    /**
     * @param arSupported whether the calling app can show AR objects (§3.15); an app that cannot
     *     plays an AR_OBJECT checkpoint as ARRIVE.
     */
    @Transactional
    public QuestRunResponse startRun(UUID userId, UUID questId, UUID tripId, boolean arSupported) {
        Quest quest = questRepository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        if (quest.questStatus() != QuestStatus.PUBLISHED || quest.getPublishedVersionId() == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found");
        }
        QuestRun open = runRepository.findOpenRun(questId, userId).orElse(null);
        if (open != null) {
            noteArSupport(open, userId, arSupported);
            return runState(userId, open);
        }
        QuestVersion version = questRepository.loadVersionGraph(quest.getPublishedVersionId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest content missing"));
        requireEntitled(quest, version, userId);

        LocalDateTime now = LocalDateTime.now();
        int expiryDays = config.getInt(BusinessConfigKey.QUEST_RUN_EXPIRY_DAYS);
        QuestRun run = QuestRun.builder()
                .id(UUID.randomUUID())
                .questId(questId)
                .questVersionId(version.getId())
                .ownerUserId(userId)
                .tripId(tripId)
                .status(QuestRunStatus.IN_PROGRESS.name())
                .startedAt(now)
                .lastActivityAt(now)
                .expiresAt(now.plusDays(expiryDays))
                .dataVersion(1L)
                .createdAt(now)
                .build();
        runRepository.insertRun(run);
        runRepository.insertMember(QuestRunMember.builder()
                .id(UUID.randomUUID())
                .runId(run.getId())
                .userId(userId)
                .joinedAt(now)
                .verification("UNVERIFIED")
                .presenceCheckpoints(0)
                .rewarded(false)
                .arSupported(arSupported)
                .build());
        return runState(userId, run);
    }

    @Transactional(readOnly = true)
    public QuestRunResponse getRun(UUID userId, UUID runId) {
        return runState(userId, requireRun(runId));
    }

    /** As {@link #getRun(UUID, UUID)}, first recording that the member's app can now show AR. */
    @Transactional
    public QuestRunResponse getRun(UUID userId, UUID runId, boolean arSupported) {
        QuestRun run = requireRun(runId);
        noteArSupport(run, userId, arSupported);
        return runState(userId, run);
    }

    /** Switches AR on for the member once their app says it can show it; never switches it off. */
    private void noteArSupport(QuestRun run, UUID userId, boolean arSupported) {
        if (!arSupported) {
            return;
        }
        runRepository.findMember(run.getId(), userId)
                .filter(member -> !member.isArSupported())
                .ifPresent(member -> runRepository.markMemberArSupported(member.getId()));
    }

    /** Join a run as a group member (§3.13), capped at GROUP_MAX_PLAYERS. Idempotent per user. */
    @Transactional
    public QuestRunResponse joinRun(UUID userId, UUID runId) {
        return joinRun(userId, runId, false);
    }

    @Transactional
    public QuestRunResponse joinRun(UUID userId, UUID runId, boolean arSupported) {
        QuestRun run = requireActiveRun(runId);
        if (runRepository.findMember(runId, userId).isPresent()) {
            noteArSupport(run, userId, arSupported);
            return runState(userId, run);
        }
        int max = config.getInt(BusinessConfigKey.QUEST_GROUP_MAX_PLAYERS);
        if (runRepository.findMembers(runId).size() >= max) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This group is full");
        }
        runRepository.insertMember(QuestRunMember.builder()
                .id(UUID.randomUUID()).runId(runId).userId(userId).joinedAt(LocalDateTime.now())
                .verification("UNVERIFIED").presenceCheckpoints(0).rewarded(false).arSupported(arSupported)
                .build());
        return runState(userId, run);
    }

    @Transactional
    public QuestArrivalResponse submitSample(UUID userId, UUID runId, QuestSampleRequest request) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        QuestCheckpoint checkpoint = checkpoint(version, request.checkpointId());
        LocalDateTime now = LocalDateTime.now();

        QuestRunCheckpoint rc = runRepository.findRunCheckpoint(member.getId(), checkpoint.getId())
                .orElseGet(() -> newRunCheckpoint(run, member, checkpoint));

        // The raw sample is kept briefly for the override-audit and health jobs (§7.6).
        UUID sampleId = UUID.randomUUID();
        runRepository.insertLocationSample(QuestLocationSample.builder()
                .id(sampleId).runId(run.getId()).memberId(member.getId()).checkpointId(checkpoint.getId())
                .latitude(request.latitude()).longitude(request.longitude())
                .accuracyMeters(request.accuracyMeters()).capturedAt(request.capturedAt() == null ? now : request.capturedAt())
                .createdAt(now).build());

        int requiredStreak = config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES);
        VerificationTarget target = unlockTarget(checkpoint);
        CheckinVerifier.Assessment assessment = checkinVerifier.assess(
                target, request.latitude(), request.longitude(), request.accuracyMeters());

        boolean within = Boolean.TRUE.equals(assessment.withinPlaceArea())
                && Boolean.TRUE.equals(assessment.accuracyAcceptable());
        int streak = within ? Math.min(intOf(rc.getStableStreak()) + 1, requiredStreak) : 0;

        boolean arrived = rc.isUnlocked();
        if (request.gpsOverride()) {
            // Manual override: unlock now and flag it so the health job can watch override rates (§3.8).
            rc.setGpsOverride(true);
            rc.setUnlockedAt(rc.getUnlockedAt() == null ? now : rc.getUnlockedAt());
            arrived = true;
            streak = requiredStreak;
        } else if (!rc.isUnlocked() && streak >= requiredStreak) {
            rc.setUnlockedAt(now);
            arrived = true;
        }

        rc.setStableStreak(streak);
        rc.setLastSampleAt(now);
        rc.setLastSampleLat(request.latitude());
        rc.setLastSampleLng(request.longitude());
        rc.setLastSampleId(sampleId);
        rc.setAccuracyMeters(request.accuracyMeters());
        if (assessment.distanceMeters() != null) {
            rc.setDistanceMeters(BigDecimal.valueOf(assessment.distanceMeters()));
        }
        runRepository.updateRunCheckpointSample(rc);
        runRepository.touchRun(run.getId(), now);

        return new QuestArrivalResponse(arrived, streak, requiredStreak,
                assessment.distanceMeters(), Boolean.TRUE.equals(assessment.accuracyAcceptable()));
    }

    @Transactional
    public QuestAnswerResponse answerQuestion(UUID userId, UUID runId, UUID questionId, QuestAnswerRequest request) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        QuestCheckpoint checkpoint = checkpointOfQuestion(version, questionId);
        QuestQuestion question = checkpoint.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Question not found"));

        QuestRunCheckpoint rc = runRepository.findRunCheckpoint(member.getId(), checkpoint.getId()).orElse(null);
        if (rc == null || !rc.isUnlocked()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Arrive at the checkpoint first");
        }

        int maxGuesses = config.getInt(BusinessConfigKey.QUEST_MAX_GUESS_ATTEMPTS);
        QuestRunQuestion rq = runRepository.findRunQuestion(member.getId(), questionId)
                .orElseGet(() -> newRunQuestion(run, member, questionId));
        if (rq.isCorrect()) {
            return new QuestAnswerResponse(true, intOf(rq.getGuessCount()), maxGuesses, false,
                    checkpointCleared(checkpoint, member));
        }
        if (intOf(rq.getGuessCount()) >= maxGuesses) {
            return new QuestAnswerResponse(false, intOf(rq.getGuessCount()), maxGuesses, true, false);
        }

        boolean correct = grade(question, request);
        rq.setGuessCount(intOf(rq.getGuessCount()) + 1);
        if (correct) {
            rq.setCorrect(true);
            rq.setAnsweredAt(LocalDateTime.now());
        }
        runRepository.updateRunQuestion(rq);
        runRepository.touchRun(run.getId(), LocalDateTime.now());

        boolean cleared = correct && checkpointCleared(checkpoint, member);
        return new QuestAnswerResponse(correct, intOf(rq.getGuessCount()), maxGuesses,
                intOf(rq.getGuessCount()) >= maxGuesses && !correct, cleared);
    }

    @Transactional
    public QuestRunResponse complete(UUID userId, UUID runId) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        if (!allCheckpointsCleared(version, member)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Not every checkpoint is cleared yet");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!runRepository.updateRunStatus(run.getId(), run.getDataVersion(),
                QuestRunStatus.COMPLETED.name(), now, now)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Run changed elsewhere; reload");
        }
        grantCompletionRewards(run, version);
        return getRun(userId, runId);
    }

    // --- local-first play ---------------------------------------------------------------

    /**
     * The whole published version, answers included, for playing on the phone. Only for a player
     * who may start it (free, unlocked, or the creator): the same gate as {@link #startRun}.
     */
    @Transactional(readOnly = true)
    public QuestPackResponse pack(UUID userId, UUID questId) {
        return pack(userId, questId, false);
    }

    /** @param arSupported whether the calling app can show AR objects; otherwise AR_OBJECT is ARRIVE. */
    @Transactional(readOnly = true)
    public QuestPackResponse pack(UUID userId, UUID questId, boolean arSupported) {
        Quest quest = questRepository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        if (quest.questStatus() != QuestStatus.PUBLISHED || quest.getPublishedVersionId() == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found");
        }
        QuestVersion version = questRepository.loadVersionGraph(quest.getPublishedVersionId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest content missing"));
        requireEntitled(quest, version, userId);
        boolean creator = userId.equals(creatorUserId(questId));

        QuestPackResponse.Settings settings = new QuestPackResponse.Settings(
                config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES),
                config.getInt(BusinessConfigKey.QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS),
                config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS),
                config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS),
                config.getInt(BusinessConfigKey.QUEST_MAX_GUESS_ATTEMPTS),
                config.getInt(BusinessConfigKey.QUEST_PROXIMITY_MIN_INTERVAL_SECONDS),
                config.getInt(BusinessConfigKey.QUEST_AR_INTERACT_RADIUS_METERS));
        ArContext ar = arContext(version, arSupported);
        List<QuestPackResponse.Checkpoint> checkpoints = version.getCheckpoints().stream()
                .map(cp -> packCheckpoint(cp, creator, ar))
                .toList();
        return new QuestPackResponse(questId, version.getId(), intOf(version.getVersion()),
                intOf(version.getContentRevision()), version.getTitle(), version.getSummary(), version.getCoverUrl(),
                version.getContentLanguage(), intOf(version.getPriceStars()), intOf(version.getRewardStars()),
                settings, checkpoints);
    }

    private QuestPackResponse.Checkpoint packCheckpoint(QuestCheckpoint cp, boolean creator, ArContext ar) {
        return new QuestPackResponse.Checkpoint(
                cp.getId(), intOf(cp.getSortOrder()), cp.getName(), cp.getCategory(),
                cp.getLatitude(), cp.getLongitude(), cp.getRadiusM(), cp.isRequiresCheckin(),
                json.readList(cp.getImageUrls(), String.class), cp.find().name(),
                cp.getSearchCenterLat(), cp.getSearchCenterLng(), cp.getSearchRadiusM(), cp.isHotColdEnabled(),
                effectiveMode(cp, ar.supported()).name(), cp.getMinStops(), cp.getStory(), cp.getStoryAudioUrl(),
                cp.getStoryAudioSeconds(),
                cp.getClues().stream().map(c -> new QuestPackResponse.Clue(intOf(c.getTier()), c.getKind(),
                        creator ? 0 : intOf(c.getCostStars()), c.getText(), c.getImageUrl())).toList(),
                cp.getStops().stream().map(stop -> new QuestPackResponse.Stop(stop.getId(),
                        intOf(stop.getSortOrder()), stop.getName(), stop.getCategory(), stop.getLatitude(),
                        stop.getLongitude(), intOf(stop.getRadiusM()), stop.getStory(),
                        json.readList(stop.getImageUrls(), String.class), stop.getAudioUrl(),
                        stop.getAudioSeconds())).toList(),
                cp.getQuestions().stream().map(q -> new QuestPackResponse.Question(q.getId(),
                        intOf(q.getSortOrder()), q.isRequired(), q.isBonus(), q.getType(), q.getPrompt(),
                        q.getImageMediaId(), q.getAnswerPlain(), json.readList(q.getAnswerVariants(), String.class),
                        q.getNumberTolerance(), q.getHintTier1(), q.getHintTier2(), q.getHintTier3(),
                        q.getChoices().stream().map(c -> new QuestPackResponse.Choice(c.getId(),
                                intOf(c.getSortOrder()), c.getContent(), c.isCorrect())).toList())).toList(),
                arObjectView(cp, ar, true));
    }

    /**
     * Records a run played on the phone (local-first play). The server replays it rather than
     * trusting it: an arrival counts only when its position lands inside the unlock circle (with
     * the fix's accuracy as slack, up to CHECKIN_MAX_ACCURACY_METERS), an answer only when it grades
     * right here, a storytelling point only when heard inside its radius, and each clue is charged
     * now. The run is COMPLETED and rewarded only when every checkpoint holds up; otherwise it is
     * kept as ABANDONED. Idempotent on the phone's run id.
     */
    @Transactional
    public QuestLocalRunResponse syncLocalRun(UUID userId, UUID questId, QuestLocalRunRequest request) {
        return syncLocalRun(userId, questId, request, false);
    }

    /** @param arSupported whether the app that played it could show AR; if not, AR_OBJECT was ARRIVE. */
    @Transactional
    public QuestLocalRunResponse syncLocalRun(UUID userId, UUID questId, QuestLocalRunRequest request,
                                              boolean arSupported) {
        QuestRun existing = runRepository.findRunByClientId(userId, request.clientRunId()).orElse(null);
        if (existing != null) {
            return localRunSummary(userId, existing, 0);
        }
        Quest quest = questRepository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        QuestVersion version = questRepository.loadVersionGraph(request.versionId())
                .filter(v -> questId.equals(v.getQuestId()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest version not found"));
        requireEntitled(quest, version, userId);

        LocalDateTime now = LocalDateTime.now();
        // Terminal from the start: the open-run index allows one IN_PROGRESS run per quest, and a
        // replayed run is never played further on the server.
        QuestRun run = QuestRun.builder()
                .id(UUID.randomUUID()).questId(questId).questVersionId(version.getId()).ownerUserId(userId)
                .status(QuestRunStatus.ABANDONED.name())
                .startedAt(request.startedAt() == null ? now : request.startedAt())
                .lastActivityAt(now).dataVersion(1L).createdAt(now).clientRunId(request.clientRunId())
                .build();
        runRepository.insertRun(run);
        QuestRunMember member = QuestRunMember.builder()
                .id(UUID.randomUUID()).runId(run.getId()).userId(userId).joinedAt(run.getStartedAt())
                .verification("UNVERIFIED").presenceCheckpoints(0).rewarded(false).arSupported(arSupported)
                .build();
        runRepository.insertMember(member);

        int maxAccuracy = config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS);
        int required = config.getInt(BusinessConfigKey.QUEST_ARRIVAL_STABLE_SAMPLES);
        for (QuestLocalRunRequest.Arrival arrival : nonNull(request.arrivals())) {
            QuestCheckpoint cp = findCheckpoint(version, arrival.checkpointId());
            if (cp == null || arrival.latitude() == null || arrival.longitude() == null
                    || runRepository.findRunCheckpoint(member.getId(), cp.getId()).isPresent()) {
                continue;
            }
            VerificationTarget target = unlockTarget(cp);
            Double distance = GeoDistance.betweenOrNull(arrival.latitude(), arrival.longitude(),
                    target.latitude(), target.longitude());
            boolean inside = within(distance, target.effectiveRadiusMeters(), arrival.accuracyMeters(), maxAccuracy);
            LocalDateTime at = arrival.arrivedAt() == null ? now : arrival.arrivedAt();
            runRepository.insertRunCheckpoint(QuestRunCheckpoint.builder()
                    .id(UUID.randomUUID()).runId(run.getId()).memberId(member.getId()).checkpointId(cp.getId())
                    .gpsOverride(false).stableStreak(inside ? required : 0)
                    .distanceMeters(distance == null ? null : BigDecimal.valueOf(distance)
                            .setScale(2, java.math.RoundingMode.HALF_UP))
                    .accuracyMeters(arrival.accuracyMeters()).lastSampleAt(at)
                    .lastSampleLat(arrival.latitude()).lastSampleLng(arrival.longitude())
                    .unlockedAt(inside ? at : null).dataVersion(1L).build());
            if (inside && cp.isRequiresCheckin()) {
                // The phone cannot post a check-in offline; arriving in person stands in for it.
                runRepository.findRunCheckpoint(member.getId(), cp.getId()).ifPresent(rc ->
                        runRepository.updateRunCheckpointCheckin(rc.getId(), null, "WAIVED"));
            }
        }

        if (arSupported) {
            int interact = config.getInt(BusinessConfigKey.QUEST_AR_INTERACT_RADIUS_METERS);
            for (QuestLocalRunRequest.ArTap tap : nonNull(request.arTaps())) {
                QuestCheckpoint cp = findCheckpoint(version, tap.checkpointId());
                if (cp == null || cp.completion() != QuestCompletionMode.AR_OBJECT) {
                    continue;
                }
                recordArTap(run, member, cp, tap.latitude(), tap.longitude(), tap.accuracyMeters(),
                        tap.anchorMode(), tap.tappedAt() == null ? now : tap.tappedAt(), interact, maxAccuracy);
            }
        }

        int maxGuesses = config.getInt(BusinessConfigKey.QUEST_MAX_GUESS_ATTEMPTS);
        for (QuestLocalRunRequest.Answer answer : nonNull(request.answers())) {
            QuestCheckpoint cp = version.getCheckpoints().stream()
                    .filter(c -> c.getQuestions().stream().anyMatch(q -> q.getId().equals(answer.questionId())))
                    .findFirst().orElse(null);
            if (cp == null || runRepository.findRunQuestion(member.getId(), answer.questionId()).isPresent()) {
                continue;
            }
            QuestQuestion question = cp.getQuestions().stream()
                    .filter(q -> q.getId().equals(answer.questionId())).findFirst().orElseThrow();
            int guesses = Math.clamp(answer.guessCount() == null ? 1 : answer.guessCount(), 1, maxGuesses);
            boolean correct = grade(question, new QuestAnswerRequest(answer.text(), answer.choiceIds()));
            QuestRunQuestion rq = newRunQuestion(run, member, question.getId());
            rq.setGuessCount(guesses);
            rq.setCorrect(correct);
            rq.setAnsweredAt(correct ? now : null);
            runRepository.updateRunQuestion(rq);
        }

        boolean creator = userId.equals(creatorUserId(questId));
        int unpaid = 0;
        for (QuestLocalRunRequest.Clue used : nonNull(request.clues())) {
            QuestCheckpoint cp = findCheckpoint(version, used.checkpointId());
            QuestCheckpointClue clue = cp == null ? null : cp.getClues().stream()
                    .filter(c -> intOf(c.getTier()) == used.tier()).findFirst().orElse(null);
            if (clue == null) {
                continue;
            }
            int cost = creator ? 0 : intOf(clue.getCostStars());
            UUID transactionId = null;
            if (cost > 0) {
                try {
                    StarTransaction spent = starService.spend(userId, cost, "QUEST_CLUE",
                            "quest_clue:" + run.getId() + ":" + cp.getId() + ":" + used.tier() + ":" + userId,
                            "Quest finding clue");
                    transactionId = spent == null ? null : spent.getId();
                } catch (BusinessException notEnoughStars) {
                    unpaid++;
                    cost = 0;
                }
            }
            boolean inserted = runRepository.insertRunClue(QuestRunClue.builder()
                    .id(UUID.randomUUID()).runId(run.getId()).memberId(member.getId()).checkpointId(cp.getId())
                    .tier(used.tier()).starsSpent(cost).starTransactionId(transactionId)
                    .boughtAt(used.boughtAt() == null ? now : used.boughtAt()).build());
            if (inserted && cost > 0) {
                economyService.creditClueSale(questId, run.getId(), userId, cost,
                        "clue:" + run.getId() + ":" + cp.getId() + ":" + used.tier() + ":" + userId);
            }
        }

        for (QuestLocalRunRequest.StopVisit visit : nonNull(request.stopVisits())) {
            QuestCheckpoint cp = version.getCheckpoints().stream()
                    .filter(c -> c.getStops().stream().anyMatch(stop -> stop.getId().equals(visit.stopId())))
                    .findFirst().orElse(null);
            if (cp == null) {
                continue;
            }
            QuestCheckpointStop stop = cp.getStops().stream()
                    .filter(candidate -> candidate.getId().equals(visit.stopId())).findFirst().orElseThrow();
            boolean gps = visit.latitude() != null && visit.longitude() != null;
            boolean counts = gps && within(GeoDistance.betweenOrNull(visit.latitude(), visit.longitude(),
                    stop.getLatitude(), stop.getLongitude()), intOf(stop.getRadiusM()), visit.accuracyMeters(),
                    maxAccuracy);
            runRepository.upsertRunStopVisit(QuestRunStopVisit.builder()
                    .id(UUID.randomUUID()).runId(run.getId()).memberId(member.getId()).checkpointId(cp.getId())
                    .stopId(stop.getId()).via(gps ? "GPS" : "TAP").countsTowardCompletion(counts)
                    .visitedAt(visit.visitedAt() == null ? now : visit.visitedAt()).build());
        }

        if (request.completedAt() != null && allCheckpointsCleared(version, member)
                && runRepository.updateRunStatus(run.getId(), run.getDataVersion(),
                        QuestRunStatus.COMPLETED.name(), request.completedAt(), now)) {
            if (unpaid == 0) {
                grantCompletionRewards(run, version);
            }
        }
        return localRunSummary(userId, runRepository.findRunById(run.getId()).orElse(run), unpaid);
    }

    private QuestLocalRunResponse localRunSummary(UUID userId, QuestRun run, int unpaid) {
        QuestVersion version = snapshot(run);
        QuestRunMember member = requireMember(run, userId);
        MemberProgress p = progressOf(member);
        int cleared = (int) version.getCheckpoints().stream().filter(cp -> isCheckpointCleared(cp, p)).count();
        boolean completed = QuestRunStatus.COMPLETED.name().equals(run.getStatus());
        boolean rewarded = runRepository.findMember(run.getId(), userId)
                .map(QuestRunMember::isRewarded).orElse(false);
        return new QuestLocalRunResponse(run.getId(), run.getStatus(), completed, rewarded, cleared,
                version.getCheckpoints().size(), unpaid);
    }

    /** Inside the circle, with the fix's own accuracy (capped) as slack. */
    private static boolean within(Double distance, int radius, BigDecimal accuracy, int maxAccuracy) {
        if (distance == null) {
            return false;
        }
        double slack = accuracy == null ? 0 : Math.min(Math.max(accuracy.doubleValue(), 0), maxAccuracy);
        return distance <= radius + slack;
    }

    private static QuestCheckpoint findCheckpoint(QuestVersion version, UUID checkpointId) {
        return version.getCheckpoints().stream()
                .filter(cp -> cp.getId().equals(checkpointId)).findFirst().orElse(null);
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }

    // --- AR objects (§3.15) --------------------------------------------------------------

    /**
     * The member tapped the current checkpoint's AR object. It counts when the phone was within
     * reach of the object: the interact radius (plus the zone of a wandering one), with the fix's
     * own accuracy as slack. An accepted tap is also proof of presence, so it unlocks the checkpoint
     * if the arrival samples had not yet. The first accepted tap wins; a second changes nothing.
     */
    @Transactional
    public QuestArTapResponse tapArObject(UUID userId, UUID runId, UUID checkpointId, QuestArTapRequest request) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        if (!member.isArSupported()) {
            // The app sending a tap can show AR, whatever it said when the run started.
            runRepository.markMemberArSupported(member.getId());
            member.setArSupported(true);
        }
        QuestVersion version = snapshot(run);
        QuestCheckpoint cp = requireCurrent(version, member, checkpointId,
                "The AR object is only for the checkpoint you are on");
        if (cp.completion() != QuestCompletionMode.AR_OBJECT) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "This checkpoint has no AR object");
        }
        boolean accepted = recordArTap(run, member, cp, request.latitude(), request.longitude(),
                request.accuracyMeters(), request.anchorMode(), LocalDateTime.now(),
                config.getInt(BusinessConfigKey.QUEST_AR_INTERACT_RADIUS_METERS),
                config.getInt(BusinessConfigKey.CHECKIN_MAX_ACCURACY_METERS));
        if (accepted) {
            runRepository.touchRun(runId, LocalDateTime.now());
        }
        boolean cleared = accepted && isCheckpointCleared(cp, progressOf(member));
        QuestArObjectView revealed = accepted ? arObjectView(cp, arContext(version, true), true) : null;
        return new QuestArTapResponse(accepted, cleared, runState(userId, run), revealed);
    }

    /** Records the tap when it lands within reach; false (and nothing written) when it does not. */
    private boolean recordArTap(QuestRun run, QuestRunMember member, QuestCheckpoint cp, BigDecimal latitude,
                                BigDecimal longitude, BigDecimal accuracy, String anchorMode, LocalDateTime at,
                                int interactRadius, int maxAccuracy) {
        QuestArObject object = arObjectOf(cp);
        if (object == null || object.latitude() == null || object.longitude() == null) {
            return false;
        }
        int reach = interactRadius + (QuestArBehavior.WANDER.name().equals(object.behavior())
                ? intOf(object.wanderRadiusM()) : 0);
        Double distance = GeoDistance.betweenOrNull(latitude, longitude, object.latitude(), object.longitude());
        if (!within(distance, reach, accuracy, maxAccuracy)) {
            return false;
        }
        QuestRunCheckpoint rc = runRepository.findRunCheckpoint(member.getId(), cp.getId())
                .orElseGet(() -> newRunCheckpoint(run, member, cp));
        if (rc.getArTappedAt() != null) {
            return true;
        }
        rc.setArTappedAt(at);
        rc.setArTapLat(latitude);
        rc.setArTapLng(longitude);
        rc.setArTapAccuracy(accuracy);
        rc.setArAnchorMode(arAnchorMode(anchorMode));
        rc.setUnlockedAt(at);
        runRepository.updateRunCheckpointArTap(rc);
        return true;
    }

    private static String arAnchorMode(String value) {
        return value != null && Set.of("APPROX", "IMAGE", "FALLBACK").contains(value) ? value : null;
    }

    private QuestArObject arObjectOf(QuestCheckpoint cp) {
        return cp.getArObject() == null ? null : json.read(cp.getArObject(), QuestArObject.class, null);
    }

    /** AR_OBJECT plays as ARRIVE for a member whose app cannot show AR (§3.15). */
    private static QuestCompletionMode effectiveMode(QuestCheckpoint cp, boolean arSupported) {
        QuestCompletionMode mode = cp.completion();
        return mode == QuestCompletionMode.AR_OBJECT && !arSupported ? QuestCompletionMode.ARRIVE : mode;
    }

    /** The AR library assets a version uses, looked up once, and whether the app can show them. */
    private record ArContext(boolean supported, Map<UUID, QuestArObjectAsset> assets, int interactRadiusM) {
    }

    private ArContext arContext(QuestVersion version, boolean supported) {
        if (!supported) {
            return new ArContext(false, Map.of(), 0);
        }
        List<UUID> assetIds = version.getCheckpoints().stream()
                .filter(cp -> cp.completion() == QuestCompletionMode.AR_OBJECT)
                .map(this::arObjectOf)
                .filter(java.util.Objects::nonNull)
                .map(QuestArObject::assetId)
                .toList();
        return new ArContext(true, questRepository.findArObjectAssets(assetIds),
                config.getInt(BusinessConfigKey.QUEST_AR_INTERACT_RADIUS_METERS));
    }

    /**
     * The object as the app shows it, or null when the checkpoint has none, the app cannot show AR,
     * or its asset is gone. {@code reveal} adds what the player learns on tapping.
     */
    private QuestArObjectView arObjectView(QuestCheckpoint cp, ArContext ar, boolean reveal) {
        if (!ar.supported() || cp.completion() != QuestCompletionMode.AR_OBJECT) {
            return null;
        }
        QuestArObject o = arObjectOf(cp);
        QuestArObjectAsset asset = o == null || o.assetId() == null ? null : ar.assets().get(o.assetId());
        if (asset == null) {
            return null;
        }
        List<QuestArObjectView.Clip> clips = json.readMaps(asset.getClips()).stream()
                .map(clip -> new QuestArObjectView.Clip(String.valueOf(clip.get("name")),
                        clip.get("seconds") instanceof Number n ? n.doubleValue() : 0))
                .toList();
        List<QuestArObjectView.Marker> markers = o.markersOrEmpty().stream()
                .filter(QuestArObject.Marker::placed)
                .map(m -> new QuestArObjectView.Marker(m.imageUrl(), m.widthM(),
                        m.offset() == null ? null : m.offset().x(), m.offset() == null ? null : m.offset().y(),
                        m.offset() == null ? null : m.offset().z(), m.yawDeg()))
                .toList();
        return new QuestArObjectView(asset.getId(), asset.getName(), asset.getGlbUrl(), asset.getUsdzUrl(),
                asset.getThumbnailUrl(), asset.getHeightM(), clips, asset.isCanWander(), o.behavior(),
                o.anchorMode(), o.latitude(), o.longitude(), o.headingDeg(), intOf(o.spawnRadiusM()),
                o.wanderRadiusM(), ar.interactRadiusM(), o.scale(), markers, o.title(),
                reveal ? o.description() : null, reveal ? o.imageUrlsOrEmpty() : List.of(),
                reveal ? o.audioUrl() : null, reveal ? o.audioSeconds() : null, o.elevationM());
    }

    // --- dynamic checkpoints (§3.14) ----------------------------------------------------

    /**
     * Buys one tier of the current AREA checkpoint's finding-clue ladder (§3.14.1). Tiers are bought
     * in order; buying one already owned changes nothing. The Stars key carries the user, so in a
     * group each member pays for their own clue (the QUEST_HINT trap of §3.5). The creator playing
     * their own quest gets clues free and earns nothing from them.
     */
    @Transactional
    public QuestRunResponse buyClue(UUID userId, UUID runId, UUID checkpointId, int tier) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        QuestCheckpoint cp = requireCurrent(version, member, checkpointId, "Clues are only for the checkpoint you are on");
        if (!cp.isArea()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "This checkpoint has no finding clues");
        }
        QuestCheckpointClue clue = cp.getClues().stream()
                .filter(c -> intOf(c.getTier()) == tier).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Clue not found"));
        Set<Integer> bought = progressOf(member).cluesBought(cp.getId());
        if (bought.contains(tier)) {
            return runState(userId, run);
        }
        if (tier > 1 && !bought.contains(tier - 1)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Get the earlier clues first");
        }

        boolean creator = userId.equals(creatorUserId(run.getQuestId()));
        int cost = creator ? 0 : intOf(clue.getCostStars());
        UUID transactionId = null;
        if (cost > 0) {
            StarTransaction spent = starService.spend(userId, cost, "QUEST_CLUE",
                    "quest_clue:" + runId + ":" + cp.getId() + ":" + tier + ":" + userId, "Quest finding clue");
            transactionId = spent == null ? null : spent.getId();
        }
        LocalDateTime now = LocalDateTime.now();
        boolean inserted = runRepository.insertRunClue(QuestRunClue.builder()
                .id(UUID.randomUUID()).runId(runId).memberId(member.getId()).checkpointId(cp.getId())
                .tier(tier).starsSpent(cost).starTransactionId(transactionId).boughtAt(now).build());
        if (inserted && cost > 0) {
            economyService.creditClueSale(run.getQuestId(), runId, userId, cost,
                    "clue:" + runId + ":" + cp.getId() + ":" + tier + ":" + userId);
        }
        runRepository.touchRun(runId, now);
        return runState(userId, run);
    }

    /**
     * Hot/cold for the current AREA checkpoint (§3.14.1): the band of the player's distance to the
     * real spot and whether they moved closer, never the distance itself. A question inside
     * PROXIMITY_MIN_INTERVAL_SECONDS gets the previous answer again. Allowed after unlocking too:
     * the player may still be looking for the object.
     */
    @Transactional
    public QuestProximityResponse proximity(UUID userId, UUID runId, UUID checkpointId,
                                            QuestProximityRequest request) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        QuestCheckpoint cp = requireCurrent(version, member, checkpointId,
                "Hot/cold is only for the checkpoint you are on");
        if (!cp.isArea() || !cp.isHotColdEnabled()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Hot/cold is not available here");
        }
        QuestRunCheckpoint rc = runRepository.findRunCheckpoint(member.getId(), cp.getId())
                .orElseGet(() -> newRunCheckpoint(run, member, cp));
        LocalDateTime now = LocalDateTime.now();
        int interval = config.getInt(BusinessConfigKey.QUEST_PROXIMITY_MIN_INTERVAL_SECONDS);
        if (rc.getProximityBand() != null && rc.getLastProximityAt() != null
                && rc.getLastProximityAt().plusSeconds(interval).isAfter(now)) {
            return new QuestProximityResponse(rc.getProximityBand(), rc.getProximityTrend());
        }
        Double distance = GeoDistance.betweenOrNull(request.latitude(), request.longitude(),
                cp.getLatitude(), cp.getLongitude());
        if (distance == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A position is needed");
        }
        String band = proximityBand(distance);
        String trend = proximityTrend(rc.getLastProximityM() == null ? null : rc.getLastProximityM().doubleValue(),
                distance);
        rc.setLastProximityM(BigDecimal.valueOf(distance).setScale(2, java.math.RoundingMode.HALF_UP));
        rc.setLastProximityAt(now);
        rc.setProximityBand(band);
        rc.setProximityTrend(trend);
        runRepository.updateRunCheckpointProximity(rc);
        runRepository.touchRun(runId, now);
        return new QuestProximityResponse(band, trend);
    }

    public static String proximityBand(double distanceM) {
        if (distanceM <= HOT_WITHIN_M) {
            return "HOT";
        }
        if (distanceM <= WARM_WITHIN_M) {
            return "WARM";
        }
        return distanceM <= COOL_WITHIN_M ? "COOL" : "COLD";
    }

    /** Null on the first question; SAME for a move under {@link #TREND_DEADBAND_M}. */
    public static String proximityTrend(Double previousM, double currentM) {
        if (previousM == null) {
            return null;
        }
        double delta = currentM - previousM;
        if (Math.abs(delta) < TREND_DEADBAND_M) {
            return "SAME";
        }
        return delta < 0 ? "CLOSER" : "FARTHER";
    }

    /**
     * Records that the member heard a storytelling point (§3.14.2). Only a point of a checkpoint the
     * member has unlocked. Idempotent per point. With a position inside the point's radius it is a
     * GPS visit and counts toward STOPS; a bare tap is kept but never counts, since it can be done
     * from home.
     */
    @Transactional
    public QuestStopVisitResponse visitStop(UUID userId, UUID runId, UUID stopId, QuestStopVisitRequest request) {
        QuestRun run = requireActiveRun(runId);
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        QuestCheckpoint cp = version.getCheckpoints().stream()
                .filter(c -> c.getStops().stream().anyMatch(stop -> stop.getId().equals(stopId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Storytelling point not found"));
        QuestCheckpointStop stop = cp.getStops().stream()
                .filter(candidate -> candidate.getId().equals(stopId)).findFirst().orElseThrow();
        QuestRunCheckpoint rc = runRepository.findRunCheckpoint(member.getId(), cp.getId()).orElse(null);
        if (rc == null || !rc.isUnlocked()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Arrive at the checkpoint first");
        }

        boolean gps = request != null && request.latitude() != null && request.longitude() != null;
        boolean counts = false;
        if (gps) {
            CheckinVerifier.Assessment assessment = checkinVerifier.assess(
                    VerificationTarget.forCoordinates(stop.getLatitude(), stop.getLongitude(), intOf(stop.getRadiusM())),
                    request.latitude(), request.longitude(), request.accuracyMeters());
            counts = Boolean.TRUE.equals(assessment.withinPlaceArea())
                    && !Boolean.FALSE.equals(assessment.accuracyAcceptable());
        }
        LocalDateTime now = LocalDateTime.now();
        runRepository.upsertRunStopVisit(QuestRunStopVisit.builder()
                .id(UUID.randomUUID()).runId(runId).memberId(member.getId()).checkpointId(cp.getId())
                .stopId(stopId).via(gps ? "GPS" : "TAP").countsTowardCompletion(counts).visitedAt(now).build());
        runRepository.touchRun(runId, now);

        MemberProgress p = progressOf(member);
        QuestRunStopVisit visit = p.visitsByStop().get(stopId);
        return new QuestStopVisitResponse(true, visit != null && visit.isCountsTowardCompletion(),
                stopsCounted(cp, p));
    }

    /** The checkpoint, when it is the member's current one; otherwise a refusal naming why. */
    private QuestCheckpoint requireCurrent(QuestVersion version, QuestRunMember member, UUID checkpointId,
                                           String message) {
        QuestCheckpoint cp = checkpoint(version, checkpointId);
        QuestCheckpoint current = currentCheckpoint(version, progressOf(member));
        if (current == null || !current.getId().equals(cp.getId())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
        }
        return cp;
    }

    // --- rewards -----------------------------------------------------------------------

    /**
     * Rewards every member who was actually present (§3.13): a member is rewarded only when the
     * fraction of checkpoints they themselves unlocked meets {@code GROUP_PRESENCE_PCT}, so one
     * person walking cannot reward the whole group.
     */
    private void grantCompletionRewards(QuestRun run, QuestVersion version) {
        int total = version.getCheckpoints().size();
        int presencePct = config.getInt(BusinessConfigKey.QUEST_GROUP_PRESENCE_PCT);
        int reward = version.getRewardStars() == null ? 0 : version.getRewardStars();
        UUID creatorUserId = creatorUserId(run.getQuestId());

        for (QuestRunMember member : runRepository.findMembers(run.getId())) {
            if (member.isRewarded()) {
                continue;
            }
            int present = (int) runRepository.findRunCheckpoints(member.getId()).stream()
                    .filter(QuestRunCheckpoint::isUnlocked).count();
            int pct = total == 0 ? 100 : present * 100 / total;
            runRepository.updateMemberVerification(member.getId(),
                    pct >= presencePct ? "VERIFIED" : "UNVERIFIED", present);
            if (pct < presencePct) {
                continue;
            }
            // One passport stamp per member (trap #3: source_id is the member id, not the run id,
            // so a six-person group produces six stamps rather than one).
            passportMapper.insertEvent(PassportEvent.builder()
                    .id(UUID.randomUUID()).userId(member.getUserId()).source("QUEST_RUN")
                    .sourceId(member.getId()).provinceCode(version.getProvinceCode())
                    .wardCode(version.getWardCode()).occurredAt(LocalDateTime.now())
                    .isVerified(true).isHidden(false).createdAt(LocalDateTime.now()).build());
            runRepository.markMemberRewarded(member.getId());

            // A creator earns no completion Stars on their own quest (§3.6). Reference key is per
            // (quest, user), NOT per run (trap #2), so replaying pays only once.
            boolean isCreator = member.getUserId().equals(creatorUserId);
            if (reward > 0 && !isCreator) {
                starService.grant(member.getUserId(), reward, "QUEST_COMPLETE",
                        "quest_complete:" + run.getQuestId() + ":" + member.getUserId(), "Quest completed");
            }
        }
    }

    /** The user behind the quest's creator profile — that member earns no completion Stars (§3.6). */
    private UUID creatorUserId(UUID questId) {
        return questRepository.findQuestById(questId)
                .flatMap(q -> questRepository.findCreatorById(q.getCreatorId()))
                .map(QuestCreatorProfile::getUserId)
                .orElse(null);
    }

    // --- state assembly ----------------------------------------------------------------

    private QuestRunResponse runState(UUID userId, QuestRun run) {
        QuestRunMember member = requireMember(run, userId);
        QuestVersion version = snapshot(run);
        MemberProgress p = progressOf(member);
        boolean creator = userId.equals(creatorUserId(run.getQuestId()));

        List<QuestCheckpoint> checkpoints = version.getCheckpoints();
        ArContext ar = arContext(version, p.arSupported());
        List<QuestRunResponse.ClearedCheckpointView> cleared = new ArrayList<>();
        QuestRunResponse.CurrentCheckpointView current = null;
        List<QuestRunResponse.UpcomingCheckpointView> upcoming = new ArrayList<>();
        int clearedCount = 0;
        for (QuestCheckpoint cp : checkpoints) {
            if (isCheckpointCleared(cp, p)) {
                clearedCount++;
                cleared.add(new QuestRunResponse.ClearedCheckpointView(
                        cp.getId(), intOf(cp.getSortOrder()), cp.getName(), cp.getCategory(), cp.getStory(),
                        json.readList(cp.getImageUrls(), String.class),
                        cp.getStoryAudioUrl(), cp.getStoryAudioSeconds(), stopViews(cp, p)));
            } else if (current == null) {
                current = currentView(run, cp, p, creator, ar);
            } else {
                // Checkpoints after the current one: public facts only, no coordinates ahead.
                upcoming.add(new QuestRunResponse.UpcomingCheckpointView(
                        cp.getId(), intOf(cp.getSortOrder()), cp.getName(), cp.getCategory(),
                        cp.find().name(), effectiveMode(cp, p.arSupported()).name(), cp.getMinStops(),
                        preview(cp)));
            }
        }
        boolean completed = current == null;
        return new QuestRunResponse(run.getId(), run.getQuestId(), run.getStatus(),
                clearedCount, checkpoints.size(), completed, cleared, current,
                config.getInt(BusinessConfigKey.QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS),
                version.getContentLanguage(), upcoming);
    }

    /** What a checkpoint holds, as counts and flags: safe to show before the player gets there. */
    private static QuestRunResponse.CheckpointPreview preview(QuestCheckpoint cp) {
        String story = cp.getStory();
        boolean audio = cp.getStoryAudioUrl() != null && !cp.getStoryAudioUrl().isBlank();
        return new QuestRunResponse.CheckpointPreview(
                cp.getQuestions().size(),
                (int) cp.getQuestions().stream().filter(QuestQuestion::isRequired).count(),
                cp.isRequiresCheckin(), cp.getStops().size(),
                story != null && !story.isBlank(), audio,
                audio ? cp.getStoryAudioSeconds() : null);
    }

    private QuestRunResponse.CurrentCheckpointView currentView(QuestRun run, QuestCheckpoint cp, MemberProgress p,
                                                               boolean creator, ArContext ar) {
        QuestRunCheckpoint rc = p.progress().get(cp.getId());
        boolean unlocked = rc != null && rc.isUnlocked();
        boolean area = cp.isArea();
        Set<Integer> bought = p.cluesBought(cp.getId());
        boolean revealed = area && cp.getClues().stream()
                .anyMatch(c -> QuestClueKind.REVEAL.name().equals(c.getKind()) && bought.contains(intOf(c.getTier())));
        // AREA hides the real spot until the REVEAL clue is bought (§3.14.1): the check stays on the server.
        boolean showSpot = !area || revealed;

        // PIN: the puzzle opens on arrival (§3.8). AREA: the task says what to look for, so it shows
        // at once; an answer is still refused until the checkpoint is unlocked.
        List<QuestRunResponse.RunQuestionView> questions = unlocked || area
                ? cp.getQuestions().stream().map(q -> questionView(run, q, p.answers().get(q.getId()))).toList()
                : List.of();
        boolean checkinDone = rc != null && (rc.getCheckinId() != null || "WAIVED".equals(rc.getCheckinState()));

        QuestRunResponse.SearchAreaView searchArea = area && cp.getSearchCenterLat() != null
                && cp.getSearchCenterLng() != null && cp.getSearchRadiusM() != null
                ? new QuestRunResponse.SearchAreaView(cp.getSearchCenterLat(), cp.getSearchCenterLng(),
                        cp.getSearchRadiusM())
                : null;
        List<QuestRunResponse.ClueView> clues = area
                ? cp.getClues().stream().map(c -> {
                    boolean owned = bought.contains(intOf(c.getTier()));
                    return new QuestRunResponse.ClueView(intOf(c.getTier()), c.getKind(),
                            creator ? 0 : intOf(c.getCostStars()), owned,
                            owned ? c.getText() : null, owned ? c.getImageUrl() : null);
                }).toList()
                : List.of();

        return new QuestRunResponse.CurrentCheckpointView(
                cp.getId(), intOf(cp.getSortOrder()), cp.getName(), cp.getCategory(),
                showSpot ? cp.getLatitude() : null, showSpot ? cp.getLongitude() : null,
                showSpot ? cp.getRadiusM() : null, cp.isRequiresCheckin(), checkinDone, unlocked,
                rc == null ? 0 : intOf(rc.getStableStreak()),
                json.readList(cp.getImageUrls(), String.class), questions,
                cp.find().name(), searchArea, area && cp.isHotColdEnabled(),
                effectiveMode(cp, p.arSupported()).name(), cp.getMinStops(),
                unlocked ? cp.getStory() : null,
                unlocked ? cp.getStoryAudioUrl() : null,
                unlocked ? cp.getStoryAudioSeconds() : null,
                clues,
                unlocked ? stopViews(cp, p) : List.of(),
                stopsCounted(cp, p), revealed, preview(cp),
                arObjectView(cp, ar, rc != null && rc.getArTappedAt() != null),
                rc != null && rc.getArTappedAt() != null);
    }

    private List<QuestRunResponse.StopView> stopViews(QuestCheckpoint cp, MemberProgress p) {
        return cp.getStops().stream().map(stop -> {
            QuestRunStopVisit visit = p.visitsByStop().get(stop.getId());
            return new QuestRunResponse.StopView(stop.getId(), intOf(stop.getSortOrder()), stop.getName(),
                    stop.getCategory(), stop.getLatitude(), stop.getLongitude(), intOf(stop.getRadiusM()),
                    stop.getStory(), json.readList(stop.getImageUrls(), String.class), stop.getAudioUrl(),
                    stop.getAudioSeconds(), visit != null, visit != null && visit.isCountsTowardCompletion());
        }).toList();
    }

    private static int stopsCounted(QuestCheckpoint cp, MemberProgress p) {
        return (int) cp.getStops().stream()
                .map(stop -> p.visitsByStop().get(stop.getId()))
                .filter(visit -> visit != null && visit.isCountsTowardCompletion())
                .count();
    }

    private QuestRunResponse.RunQuestionView questionView(QuestRun run, QuestQuestion q, QuestRunQuestion rq) {
        List<QuestRunResponse.RunChoiceView> choices = new ArrayList<>();
        if (QuestQuestionType.valueOf(q.getType()).isChoiceBased()) {
            List<QuestRunResponse.RunChoiceView> shuffled = q.getChoices().stream()
                    .map(c -> new QuestRunResponse.RunChoiceView(c.getId(), c.getContent()))
                    .collect(Collectors.toCollection(ArrayList::new));
            // Shuffle per (run, question): stable within a run, different across runs, so a
            // screenshot never reveals "the answer is B". Never carries the correctness flag.
            java.util.Collections.shuffle(shuffled, new Random(run.getId().hashCode() * 31L + q.getId().hashCode()));
            choices = shuffled;
        }
        return new QuestRunResponse.RunQuestionView(
                q.getId(), intOf(q.getSortOrder()), q.isRequired(), q.isBonus(), q.getType(), q.getPrompt(),
                q.getImageMediaId(), rq == null ? 0 : intOf(rq.getGuessCount()),
                rq != null && rq.isCorrect(), rq != null && rq.isSkipped(),
                boughtHint(q, rq), choices);
    }

    private String boughtHint(QuestQuestion q, QuestRunQuestion rq) {
        int tier = rq == null ? 0 : intOf(rq.getHintTierBought());
        return switch (tier) {
            case 3 -> q.getHintTier3();
            case 2 -> q.getHintTier2();
            case 1 -> q.getHintTier1();
            default -> null;
        };
    }

    // --- gates -------------------------------------------------------------------------

    /** Everything one member has done in a run, loaded once per request. */
    private record MemberProgress(
            Map<UUID, QuestRunQuestion> answers,
            Map<UUID, QuestRunCheckpoint> progress,
            Map<UUID, QuestRunStopVisit> visitsByStop,
            Map<UUID, Set<Integer>> cluesByCheckpoint,
            /** Whether the member's app shows AR objects; if not, AR_OBJECT plays as ARRIVE (§3.15). */
            boolean arSupported) {

        Set<Integer> cluesBought(UUID checkpointId) {
            return cluesByCheckpoint.getOrDefault(checkpointId, Set.of());
        }
    }

    private MemberProgress progressOf(QuestRunMember member) {
        Map<UUID, QuestRunQuestion> answers = runRepository.findRunQuestions(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunQuestion::getQuestionId, Function.identity()));
        Map<UUID, QuestRunCheckpoint> progress = runRepository.findRunCheckpoints(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunCheckpoint::getCheckpointId, Function.identity()));
        Map<UUID, QuestRunStopVisit> visits = runRepository.findRunStopVisits(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunStopVisit::getStopId, Function.identity(), (a, b) -> a));
        Map<UUID, Set<Integer>> clues = runRepository.findRunClues(member.getId()).stream()
                .collect(Collectors.groupingBy(QuestRunClue::getCheckpointId,
                        Collectors.mapping(c -> intOf(c.getTier()), Collectors.toSet())));
        return new MemberProgress(answers, progress, visits, clues, member.isArSupported());
    }

    private boolean allCheckpointsCleared(QuestVersion version, QuestRunMember member) {
        MemberProgress p = progressOf(member);
        return version.getCheckpoints().stream().allMatch(cp -> isCheckpointCleared(cp, p));
    }

    private boolean checkpointCleared(QuestCheckpoint cp, QuestRunMember member) {
        return isCheckpointCleared(cp, progressOf(member));
    }

    /** The member's first checkpoint not yet cleared, in route order; null when all are. */
    private QuestCheckpoint currentCheckpoint(QuestVersion version, MemberProgress p) {
        return version.getCheckpoints().stream()
                .filter(cp -> !isCheckpointCleared(cp, p))
                .findFirst()
                .orElse(null);
    }

    /**
     * The advance gate (§3.12, §3.14). In every mode the checkpoint must first be unlocked (ISSUES
     * B32: this used not to be required). Then:
     * <ul>
     *   <li>TASK: every required, non-bonus question correct, and the required check-in done;</li>
     *   <li>ARRIVE: nothing more — a guide-only checkpoint;</li>
     *   <li>STOPS: at least {@code min_stops} storytelling points heard by GPS, plus any required
     *       question or check-in the creator also set.</li>
     *   <li>AR_OBJECT: the AR object tapped within reach, plus any required question or check-in
     *       (§3.15); ARRIVE for a member whose app cannot show AR.</li>
     * </ul>
     */
    private boolean isCheckpointCleared(QuestCheckpoint cp, MemberProgress p) {
        QuestRunCheckpoint rc = p.progress().get(cp.getId());
        if (rc == null || !rc.isUnlocked()) {
            return false;
        }
        QuestCompletionMode mode = effectiveMode(cp, p.arSupported());
        if (mode == QuestCompletionMode.ARRIVE) {
            return true;
        }
        boolean questionsDone = cp.getQuestions().stream()
                .filter(q -> q.isRequired() && !q.isBonus())
                .allMatch(q -> {
                    QuestRunQuestion rq = p.answers().get(q.getId());
                    return rq != null && rq.isCorrect();
                });
        boolean checkinDone = !cp.isRequiresCheckin()
                || rc.getCheckinId() != null || "WAIVED".equals(rc.getCheckinState());
        if (mode == QuestCompletionMode.STOPS) {
            int min = cp.getMinStops() == null ? 1 : cp.getMinStops();
            return questionsDone && checkinDone && stopsCounted(cp, p) >= min;
        }
        if (mode == QuestCompletionMode.AR_OBJECT) {
            return questionsDone && checkinDone && rc.getArTappedAt() != null;
        }
        return questionsDone && checkinDone;
    }

    /**
     * Where a sample must land to unlock: the checkpoint's own circle for PIN; the search circle
     * for AREA (§3.14.1), falling back to the spot if a legacy row has no centre.
     */
    private VerificationTarget unlockTarget(QuestCheckpoint cp) {
        if (cp.isArea() && cp.getSearchCenterLat() != null && cp.getSearchCenterLng() != null
                && cp.getSearchRadiusM() != null) {
            return VerificationTarget.forCoordinates(cp.getSearchCenterLat(), cp.getSearchCenterLng(),
                    cp.getSearchRadiusM());
        }
        int radius = cp.getRadiusM() != null ? cp.getRadiusM()
                : config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS);
        return VerificationTarget.forCoordinates(cp.getLatitude(), cp.getLongitude(), radius);
    }

    // --- grading -----------------------------------------------------------------------

    private boolean grade(QuestQuestion question, QuestAnswerRequest request) {
        QuestQuestionType type = QuestQuestionType.valueOf(question.getType());
        return switch (type) {
            case TEXT -> grader.gradeText(question.getAnswerPlain(),
                    json.readList(question.getAnswerVariants(), String.class), request.text());
            case NUMBER -> grader.gradeNumber(question.getAnswerPlain(), question.getNumberTolerance(), request.text());
            case CHOICE -> grader.gradeChoice(question.getChoices(), request.choiceIds(), false);
            case MULTI_CHOICE -> grader.gradeChoice(question.getChoices(), request.choiceIds(), true);
            case PHOTO -> grader.gradePhoto();
        };
    }

    // --- lookups -----------------------------------------------------------------------

    private void requireEntitled(Quest quest, QuestVersion version, UUID userId) {
        int price = version.getPriceStars() == null ? 0 : version.getPriceStars();
        if (price <= 0) {
            return;
        }
        // The creator plays their own quest free, as QuestEconomyService.unlock already grants;
        // without this they would have to "unlock" their own quest before a play test.
        boolean isCreator = questRepository.findCreatorById(quest.getCreatorId())
                .map(creator -> userId.equals(creator.getUserId()))
                .orElse(false);
        if (isCreator) {
            return;
        }
        if (runRepository.findEntitlement(quest.getId(), userId).isEmpty()) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Unlock this quest before starting");
        }
    }

    /** Records a paid or free unlock (§6.2 D19). Idempotent on (quest, user). */
    @Transactional
    public void grantEntitlement(UUID questId, UUID userId, String fundingSource, String sourceReference) {
        if (runRepository.findEntitlement(questId, userId).isPresent()) {
            return;
        }
        runRepository.insertEntitlement(QuestEntitlement.builder()
                .id(UUID.randomUUID()).questId(questId).userId(userId)
                .fundingSource(fundingSource == null ? "FREE" : fundingSource)
                .sourceReference(sourceReference).createdAt(LocalDateTime.now()).build());
    }

    /**
     * Links a check-in the composer just created to its quest checkpoint (D13). Called from the
     * check-in service when a check-in carries a {@code questRunId}. A check-in is itself proof of
     * presence, so if no arrival sample preceded it the run checkpoint is created and unlocked here.
     * Best-effort: a bad run/checkpoint is ignored rather than failing the user's check-in.
     */
    @Transactional
    public void attachCheckin(UUID runId, UUID checkpointId, UUID userId, UUID checkinId) {
        if (runId == null || checkpointId == null || checkinId == null) {
            return;
        }
        QuestRun run = runRepository.findRunById(runId).orElse(null);
        QuestRunMember member = run == null ? null : runRepository.findMember(runId, userId).orElse(null);
        if (member == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        QuestRunCheckpoint rc = runRepository.findRunCheckpoint(member.getId(), checkpointId).orElse(null);
        if (rc == null) {
            rc = QuestRunCheckpoint.builder()
                    .id(UUID.randomUUID()).runId(runId).memberId(member.getId()).checkpointId(checkpointId)
                    .gpsOverride(false).stableStreak(0).unlockedAt(now).dataVersion(1L).build();
            runRepository.insertRunCheckpoint(rc);
        }
        runRepository.updateRunCheckpointCheckin(rc.getId(), checkinId, "ACTIVE");
        runRepository.touchRun(runId, now);
    }

    private QuestRun requireRun(UUID runId) {
        return runRepository.findRunById(runId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Run not found"));
    }

    private QuestRun requireActiveRun(UUID runId) {
        QuestRun run = requireRun(runId);
        if (!run.runStatus().isActive()) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This run is " + run.getStatus().toLowerCase());
        }
        return run;
    }

    private QuestRunMember requireMember(QuestRun run, UUID userId) {
        return runRepository.findMember(run.getId(), userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Run not found"));
    }

    private QuestVersion snapshot(QuestRun run) {
        return questRepository.loadVersionGraph(run.getQuestVersionId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest content missing"));
    }

    private QuestCheckpoint checkpoint(QuestVersion version, UUID checkpointId) {
        return version.getCheckpoints().stream()
                .filter(cp -> cp.getId().equals(checkpointId)).findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Checkpoint not found"));
    }

    private QuestCheckpoint checkpointOfQuestion(QuestVersion version, UUID questionId) {
        return version.getCheckpoints().stream()
                .filter(cp -> cp.getQuestions().stream().anyMatch(q -> q.getId().equals(questionId)))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Question not found"));
    }

    private QuestRunCheckpoint newRunCheckpoint(QuestRun run, QuestRunMember member, QuestCheckpoint cp) {
        QuestRunCheckpoint rc = QuestRunCheckpoint.builder()
                .id(UUID.randomUUID()).runId(run.getId()).memberId(member.getId()).checkpointId(cp.getId())
                .gpsOverride(false).stableStreak(0).dataVersion(1L).build();
        runRepository.insertRunCheckpoint(rc);
        return rc;
    }

    private QuestRunQuestion newRunQuestion(QuestRun run, QuestRunMember member, UUID questionId) {
        QuestRunQuestion rq = QuestRunQuestion.builder()
                .id(UUID.randomUUID()).runId(run.getId()).memberId(member.getId()).questionId(questionId)
                .guessCount(0).hintTierBought(0).revealed(false).skipped(false).correct(false).build();
        runRepository.insertRunQuestion(rq);
        return rq;
    }

    private static int intOf(Integer value) {
        return value == null ? 0 : value;
    }
}
