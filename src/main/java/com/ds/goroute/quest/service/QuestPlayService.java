package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestEntitlement;
import com.ds.goroute.quest.domain.QuestLocationSample;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestRun;
import com.ds.goroute.quest.domain.QuestRunCheckpoint;
import com.ds.goroute.quest.domain.QuestRunMember;
import com.ds.goroute.quest.domain.QuestRunQuestion;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.QuestAnswerRequest;
import com.ds.goroute.quest.dto.QuestAnswerResponse;
import com.ds.goroute.quest.dto.QuestArrivalResponse;
import com.ds.goroute.quest.dto.QuestRunResponse;
import com.ds.goroute.quest.dto.QuestSampleRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.quest.persistence.QuestRunRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.service.checkin.CheckinVerifier;
import com.ds.goroute.service.checkin.VerificationTarget;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.QuestQuestionType;
import com.ds.goroute.type.QuestRunStatus;
import com.ds.goroute.type.QuestStatus;
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

    @Transactional
    public QuestRunResponse startRun(UUID userId, UUID questId, UUID tripId) {
        Quest quest = questRepository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        if (quest.questStatus() != QuestStatus.PUBLISHED || quest.getPublishedVersionId() == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found");
        }
        QuestRun open = runRepository.findOpenRun(questId, userId).orElse(null);
        if (open != null) {
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
                .build());
        return runState(userId, run);
    }

    @Transactional(readOnly = true)
    public QuestRunResponse getRun(UUID userId, UUID runId) {
        return runState(userId, requireRun(runId));
    }

    /** Join a run as a group member (§3.13), capped at GROUP_MAX_PLAYERS. Idempotent per user. */
    @Transactional
    public QuestRunResponse joinRun(UUID userId, UUID runId) {
        QuestRun run = requireActiveRun(runId);
        if (runRepository.findMember(runId, userId).isPresent()) {
            return runState(userId, run);
        }
        int max = config.getInt(BusinessConfigKey.QUEST_GROUP_MAX_PLAYERS);
        if (runRepository.findMembers(runId).size() >= max) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This group is full");
        }
        runRepository.insertMember(QuestRunMember.builder()
                .id(UUID.randomUUID()).runId(runId).userId(userId).joinedAt(LocalDateTime.now())
                .verification("UNVERIFIED").presenceCheckpoints(0).rewarded(false).build());
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
        int radius = checkpoint.getRadiusM() != null ? checkpoint.getRadiusM()
                : config.getInt(BusinessConfigKey.QUEST_UNLOCK_RADIUS_METERS);
        VerificationTarget target = VerificationTarget.forCoordinates(
                checkpoint.getLatitude(), checkpoint.getLongitude(), radius);
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
        Map<UUID, QuestRunQuestion> answers = runRepository.findRunQuestions(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunQuestion::getQuestionId, Function.identity()));
        Map<UUID, QuestRunCheckpoint> progress = runRepository.findRunCheckpoints(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunCheckpoint::getCheckpointId, Function.identity()));

        List<QuestCheckpoint> checkpoints = version.getCheckpoints();
        List<QuestRunResponse.ClearedCheckpointView> cleared = new ArrayList<>();
        QuestRunResponse.CurrentCheckpointView current = null;
        int clearedCount = 0;
        for (QuestCheckpoint cp : checkpoints) {
            if (isCheckpointCleared(cp, answers, progress.get(cp.getId()))) {
                clearedCount++;
                cleared.add(new QuestRunResponse.ClearedCheckpointView(
                        cp.getId(), intOf(cp.getSortOrder()), cp.getName(), cp.getStory()));
            } else if (current == null) {
                current = currentView(run, cp, answers, progress.get(cp.getId()));
            }
            // Checkpoints after the current one are intentionally omitted: no coordinates ahead.
        }
        boolean completed = current == null;
        return new QuestRunResponse(run.getId(), run.getQuestId(), run.getStatus(),
                clearedCount, checkpoints.size(), completed, cleared, current,
                config.getInt(BusinessConfigKey.QUEST_ARRIVAL_CLIENT_INTERVAL_SECONDS));
    }

    private QuestRunResponse.CurrentCheckpointView currentView(QuestRun run, QuestCheckpoint cp,
                                                               Map<UUID, QuestRunQuestion> answers,
                                                               QuestRunCheckpoint rc) {
        boolean unlocked = rc != null && rc.isUnlocked();
        // The puzzle only opens on arrival (§3.8): before that, coordinates to navigate, no questions.
        List<QuestRunResponse.RunQuestionView> questions = unlocked
                ? cp.getQuestions().stream().map(q -> questionView(run, q, answers.get(q.getId()))).toList()
                : List.of();
        boolean checkinDone = rc != null && (rc.getCheckinId() != null || "WAIVED".equals(rc.getCheckinState()));
        return new QuestRunResponse.CurrentCheckpointView(
                cp.getId(), intOf(cp.getSortOrder()), cp.getName(), cp.getLatitude(), cp.getLongitude(),
                cp.getRadiusM(), cp.isRequiresCheckin(), checkinDone, unlocked,
                rc == null ? 0 : intOf(rc.getStableStreak()), questions);
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

    private boolean allCheckpointsCleared(QuestVersion version, QuestRunMember member) {
        Map<UUID, QuestRunQuestion> answers = runRepository.findRunQuestions(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunQuestion::getQuestionId, Function.identity()));
        Map<UUID, QuestRunCheckpoint> progress = runRepository.findRunCheckpoints(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunCheckpoint::getCheckpointId, Function.identity()));
        return version.getCheckpoints().stream()
                .allMatch(cp -> isCheckpointCleared(cp, answers, progress.get(cp.getId())));
    }

    private boolean checkpointCleared(QuestCheckpoint cp, QuestRunMember member) {
        Map<UUID, QuestRunQuestion> answers = runRepository.findRunQuestions(member.getId()).stream()
                .collect(Collectors.toMap(QuestRunQuestion::getQuestionId, Function.identity()));
        return isCheckpointCleared(cp, answers, runRepository.findRunCheckpoint(member.getId(), cp.getId()).orElse(null));
    }

    /** The advance gate (§3.12): every required, non-bonus question correct, and the check-in done. */
    private boolean isCheckpointCleared(QuestCheckpoint cp, Map<UUID, QuestRunQuestion> answers, QuestRunCheckpoint rc) {
        boolean questionsDone = cp.getQuestions().stream()
                .filter(q -> q.isRequired() && !q.isBonus())
                .allMatch(q -> {
                    QuestRunQuestion rq = answers.get(q.getId());
                    return rq != null && rq.isCorrect();
                });
        boolean checkinDone = !cp.isRequiresCheckin()
                || (rc != null && (rc.getCheckinId() != null || "WAIVED".equals(rc.getCheckinState())));
        return questionsDone && checkinDone;
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
