package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.Quest;
import com.ds.goroute.quest.domain.QuestCheckpoint;
import com.ds.goroute.quest.domain.QuestCreatorNote;
import com.ds.goroute.quest.domain.QuestCreatorProfile;
import com.ds.goroute.quest.domain.QuestQuestion;
import com.ds.goroute.quest.domain.QuestQuestionChoice;
import com.ds.goroute.quest.domain.QuestVersion;
import com.ds.goroute.quest.dto.CreateQuestRequest;
import com.ds.goroute.quest.dto.QuestDraftResponse;
import com.ds.goroute.quest.dto.QuestSummaryResponse;
import com.ds.goroute.quest.dto.SaveQuestDraftRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.ds.goroute.type.QuestOrigin;
import com.ds.goroute.type.QuestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuestBuilderServiceImpl implements QuestBuilderService {

    private static final int MAX_PAGE_SIZE = 50;

    private final QuestRepository repository;
    private final QuestDraftValidator validator;
    private final QuestCreatorGate creatorGate;
    private final MarketplaceJson json;

    @Override
    @Transactional
    public QuestDraftResponse create(UUID actorUserId, CreateQuestRequest request, boolean allowSystemOrigin) {
        // The app path is gated by the feature flag (D4); the operator/console path is not.
        if (!allowSystemOrigin) {
            creatorGate.requireCreationOpen(actorUserId);
        }
        QuestCreatorProfile creator = ensureCreator(actorUserId);
        if (creator.creatorStatus() == com.ds.goroute.type.QuestCreatorStatus.SUSPENDED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Your creator account is suspended");
        }
        QuestOrigin origin = resolveOrigin(request == null ? null : request.origin(), allowSystemOrigin);
        String language = request != null && request.contentLanguage() != null && !request.contentLanguage().isBlank()
                ? request.contentLanguage() : "vi";
        LocalDateTime now = LocalDateTime.now();

        UUID questId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        Quest quest = Quest.builder()
                .id(questId)
                .creatorId(creator.getId())
                .origin(origin.name())
                .status(QuestStatus.DRAFT.name())
                .draftVersionId(versionId)
                .pendingChangeReview(false)
                .selfApproved(false)
                .dataVersion(1L)
                .deleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        repository.insertQuest(quest);

        QuestVersion version = QuestVersion.builder()
                .id(versionId)
                .questId(questId)
                .version(1)
                .contentRevision(0)
                .changeKind("MATERIAL")
                .amenityTags(json.write(List.of()))
                .contentLanguage(language)
                .priceStars(0)
                .rewardStars(0)
                .createdBy(actorUserId)
                .createdAt(now)
                .build();
        repository.insertVersion(version);

        return getDraft(questId, actorUserId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public QuestDraftResponse getDraft(UUID questId, UUID actorUserId, boolean admin) {
        Quest quest = requireQuest(questId);
        if (!admin) {
            requireOwner(quest, actorUserId);
        }
        UUID versionId = quest.getDraftVersionId() != null ? quest.getDraftVersionId() : quest.getPublishedVersionId();
        QuestVersion version = versionId == null ? null
                : repository.loadVersionGraph(versionId).orElse(null);
        if (version == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest has no editable version");
        }
        Map<UUID, List<QuestCreatorNote>> notes = notesByCheckpoint(version);
        return QuestDraftResponse.of(quest, version, notes, json.readList(version.getAmenityTags(), String.class));
    }

    @Override
    @Transactional
    public QuestDraftResponse saveDraft(UUID questId, UUID actorUserId, SaveQuestDraftRequest request) {
        Quest quest = requireQuest(questId);
        requireOwner(quest, actorUserId);
        requireCreatorEditable(quest);
        long expected = request.getExpectedVersion() > 0 ? request.getExpectedVersion() : quest.getDataVersion();
        LocalDateTime now = LocalDateTime.now();

        // One guarded write both verifies the version and normalises DENIED → DRAFT (§3.1).
        if (!repository.updateStatus(questId, expected, QuestStatus.DRAFT.name(), null, now)) {
            throw conflict(questId);
        }

        UUID versionId = quest.getDraftVersionId();
        QuestVersion version = repository.findVersionById(versionId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Draft version missing"));
        applyScalars(version, request);
        repository.updateVersionContent(version);

        repository.clearVersionContent(versionId);
        insertGraph(versionId, actorUserId, request, now);

        return getDraft(questId, actorUserId, false);
    }

    @Override
    @Transactional
    public QuestDraftResponse submit(UUID questId, UUID actorUserId, Long expectedVersion, boolean enforceCreatorGate) {
        Quest quest = requireQuest(questId);
        QuestCreatorProfile creator = requireOwner(quest, actorUserId);
        requireCreatorEditable(quest);

        // The feature flag, cooldown and quotas that stand in for the up-front human gate (D4/§3.2).
        // The operator/console path passes enforceCreatorGate=false.
        if (enforceCreatorGate) {
            creatorGate.requireCanSubmit(creator);
        }

        QuestVersion version = repository.loadVersionGraph(quest.getDraftVersionId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Draft version missing"));
        validator.validateForSubmit(version);

        long expected = expectedVersion != null && expectedVersion > 0 ? expectedVersion : quest.getDataVersion();
        if (!repository.updateStatus(questId, expected, QuestStatus.PENDING.name(), null, LocalDateTime.now())) {
            throw conflict(questId);
        }
        if (enforceCreatorGate) {
            creatorGate.recordSubmission(creator);
        }
        return getDraft(questId, actorUserId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<QuestSummaryResponse> listMine(UUID actorUserId, String status, int page, int size) {
        int safeSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
        int safePage = Math.max(0, page);
        QuestCreatorProfile creator = repository.findCreatorByUser(actorUserId).orElse(null);
        if (creator == null) {
            return PageResponse.of(List.of(), 0, safePage, safeSize);
        }
        String normalizedStatus = status == null || status.isBlank() ? null : status;
        List<QuestSummaryResponse> items = repository
                .findQuestsByCreator(creator.getId(), normalizedStatus, safeSize, safePage * safeSize).stream()
                .map(this::toSummary)
                .toList();
        long total = repository.countQuestsByCreator(creator.getId(), normalizedStatus);
        return PageResponse.of(items, total, safePage, safeSize);
    }

    // --- helpers ------------------------------------------------------------------------

    private QuestCreatorProfile ensureCreator(UUID userId) {
        return repository.findCreatorByUser(userId).orElseGet(() -> {
            LocalDateTime now = LocalDateTime.now();
            QuestCreatorProfile creator = QuestCreatorProfile.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .status("ACTIVE")
                    .qualityScore(0)
                    .dataVersion(1L)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            repository.insertCreator(creator);
            return creator;
        });
    }

    private QuestOrigin resolveOrigin(String requested, boolean allowSystemOrigin) {
        if (requested == null || requested.isBlank()) {
            return QuestOrigin.COMMUNITY;
        }
        QuestOrigin origin;
        try {
            origin = QuestOrigin.valueOf(requested);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Unknown quest origin: " + requested);
        }
        if (origin == QuestOrigin.SYSTEM && !allowSystemOrigin) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "Only an operator can create a system quest");
        }
        return origin;
    }

    private Quest requireQuest(UUID questId) {
        return repository.findQuestById(questId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
    }

    /** The quest's owner, or a 404 to anyone else — a guessed id must not confirm a quest exists. */
    private QuestCreatorProfile requireOwner(Quest quest, UUID actorUserId) {
        QuestCreatorProfile creator = repository.findCreatorById(quest.getCreatorId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found"));
        if (!creator.getUserId().equals(actorUserId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Quest not found");
        }
        return creator;
    }

    private void requireCreatorEditable(Quest quest) {
        if (!quest.questStatus().isCreatorEditable()) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This quest is " + quest.getStatus().toLowerCase() + " and cannot be edited here");
        }
    }

    private void applyScalars(QuestVersion version, SaveQuestDraftRequest request) {
        version.setChangeKind("MATERIAL");
        version.setTitle(request.getTitle());
        version.setSummary(request.getSummary());
        version.setDescription(request.getDescription());
        version.setSafetyNotes(request.getSafetyNotes());
        version.setCoverMediaId(request.getCoverMediaId());
        version.setDifficulty(request.getDifficulty());
        version.setEstimatedMinutes(request.getEstimatedMinutes());
        version.setDistanceMeters(request.getDistanceMeters());
        version.setAmenityTags(json.write(request.getAmenityTags() == null ? List.of() : request.getAmenityTags()));
        version.setProvinceCode(request.getProvinceCode());
        version.setWardCode(request.getWardCode());
        if (request.getContentLanguage() != null && !request.getContentLanguage().isBlank()) {
            version.setContentLanguage(request.getContentLanguage());
        }
        version.setPriceStars(request.getPriceStars() == null ? 0 : Math.max(0, request.getPriceStars()));
        version.setRewardStars(request.getRewardStars() == null ? 0 : Math.max(0, request.getRewardStars()));
        version.setPlayableMonths(request.getPlayableMonths() == null ? null : json.write(request.getPlayableMonths()));
        version.setPlayableHours(request.getPlayableHours() == null ? null : json.write(request.getPlayableHours()));
        version.setRunExpiryHours(request.getRunExpiryHours());
    }

    private void insertGraph(UUID versionId, UUID actorUserId, SaveQuestDraftRequest request, LocalDateTime now) {
        List<SaveQuestDraftRequest.CheckpointInput> checkpoints =
                request.getCheckpoints() == null ? List.of() : request.getCheckpoints();
        for (int i = 0; i < checkpoints.size(); i++) {
            SaveQuestDraftRequest.CheckpointInput input = checkpoints.get(i);
            UUID checkpointId = UUID.randomUUID();
            QuestCheckpoint checkpoint = QuestCheckpoint.builder()
                    .id(checkpointId)
                    .questVersionId(versionId)
                    .sortOrder(i)
                    .name(input.getName())
                    .latitude(input.getLatitude())
                    .longitude(input.getLongitude())
                    .radiusM(input.getRadiusM())
                    .placeId(input.getPlaceId())
                    .story(input.getStory())
                    .captureSource(input.getCaptureSource() == null ? "MAP" : input.getCaptureSource())
                    .captureAccuracyMeters(input.getCaptureAccuracyMeters())
                    .capturedAt(input.getCapturedAt())
                    .requiresCheckin(input.isRequiresCheckin())
                    .createdAt(now)
                    .build();
            repository.insertCheckpoint(checkpoint);

            insertQuestions(checkpointId, input, now);
            insertNotes(checkpointId, actorUserId, input, now);
        }
    }

    private void insertQuestions(UUID checkpointId, SaveQuestDraftRequest.CheckpointInput input, LocalDateTime now) {
        List<SaveQuestDraftRequest.QuestionInput> questions =
                input.getQuestions() == null ? List.of() : input.getQuestions();
        for (int j = 0; j < questions.size(); j++) {
            SaveQuestDraftRequest.QuestionInput q = questions.get(j);
            UUID questionId = UUID.randomUUID();
            QuestQuestion question = QuestQuestion.builder()
                    .id(questionId)
                    .checkpointId(checkpointId)
                    .sortOrder(j)
                    .required(q.isRequired())
                    .bonus(q.isBonus())
                    .type(q.getType())
                    .prompt(q.getPrompt())
                    .imageMediaId(q.getImageMediaId())
                    .answerPlain(q.getAnswerPlain())
                    .answerVariants(q.getAnswerVariants() == null || q.getAnswerVariants().isEmpty()
                            ? null : json.write(q.getAnswerVariants()))
                    .numberTolerance(q.getNumberTolerance())
                    .hintTier1(q.getHintTier1())
                    .hintTier2(q.getHintTier2())
                    .hintTier3(q.getHintTier3())
                    .bonusStars(q.getBonusStars() == null ? 0 : Math.max(0, q.getBonusStars()))
                    .createdAt(now)
                    .build();
            repository.insertQuestion(question);

            List<SaveQuestDraftRequest.ChoiceInput> choices = q.getChoices() == null ? List.of() : q.getChoices();
            for (int k = 0; k < choices.size(); k++) {
                SaveQuestDraftRequest.ChoiceInput c = choices.get(k);
                repository.insertChoice(QuestQuestionChoice.builder()
                        .id(c.getId() != null ? c.getId() : UUID.randomUUID())
                        .questionId(questionId)
                        .sortOrder(k)
                        .content(c.getContent())
                        .correct(c.isCorrect())
                        .build());
            }
        }
    }

    private void insertNotes(UUID checkpointId, UUID actorUserId,
                             SaveQuestDraftRequest.CheckpointInput input, LocalDateTime now) {
        List<String> notes = input.getNotes() == null ? List.of() : input.getNotes();
        for (String note : notes) {
            if (note == null || note.isBlank()) {
                continue;
            }
            repository.insertNote(QuestCreatorNote.builder()
                    .id(UUID.randomUUID())
                    .checkpointId(checkpointId)
                    .note(note)
                    .createdBy(actorUserId)
                    .createdAt(now)
                    .build());
        }
    }

    private Map<UUID, List<QuestCreatorNote>> notesByCheckpoint(QuestVersion version) {
        List<UUID> checkpointIds = version.getCheckpoints().stream().map(QuestCheckpoint::getId).toList();
        return repository.findNotesByCheckpoints(checkpointIds).stream()
                .collect(Collectors.groupingBy(QuestCreatorNote::getCheckpointId));
    }

    private QuestSummaryResponse toSummary(Quest quest) {
        UUID versionId = quest.getDraftVersionId() != null ? quest.getDraftVersionId() : quest.getPublishedVersionId();
        String title = versionId == null ? null
                : repository.findVersionById(versionId).map(QuestVersion::getTitle).orElse(null);
        int checkpoints = repository.countCheckpoints(versionId);
        return new QuestSummaryResponse(quest.getId(), quest.getOrigin(), quest.getStatus(), title,
                checkpoints, quest.getDataVersion() == null ? 0 : quest.getDataVersion(), quest.getUpdatedAt());
    }

    private BusinessException conflict(UUID questId) {
        log.debug("Quest {} changed under a concurrent write", questId);
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                "This quest was changed somewhere else. Reload it and try again.");
    }
}
