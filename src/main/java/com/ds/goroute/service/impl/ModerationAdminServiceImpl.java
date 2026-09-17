package com.ds.goroute.service.impl;

import com.ds.goroute.utils.ErrorMessages;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ModerationPreviewRequest;
import com.ds.goroute.dto.request.ResolveModerationFlagRequest;
import com.ds.goroute.dto.request.UpsertModerationTermRequest;
import com.ds.goroute.dto.response.ModerationFlagResponse;
import com.ds.goroute.dto.response.ModerationMetricsResponse;
import com.ds.goroute.dto.response.ModerationPreviewResponse;
import com.ds.goroute.dto.response.ModerationTermAuditResponse;
import com.ds.goroute.dto.response.ModerationTermResponse;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.ModerationFlag;
import com.ds.goroute.entity.ModerationTerm;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ModerationAuditRepository;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.repository.ModerationTermRepository;
import com.ds.goroute.service.ModerationAdminService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.ContentReportStatus;
import com.ds.goroute.type.ModerationFlagStatus;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.ModerationTextNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class ModerationAdminServiceImpl implements ModerationAdminService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int FALSE_POSITIVE_RANKING_SIZE = 20;

    private final ModerationTermRepository termRepository;
    private final ModerationFlagRepository flagRepository;
    private final ModerationAuditRepository auditRepository;
    private final TextModerationService textModerationService;
    private final NotificationService notificationService;

    public ModerationAdminServiceImpl(ModerationTermRepository termRepository,
                                      ModerationFlagRepository flagRepository,
                                      ModerationAuditRepository auditRepository,
                                      TextModerationService textModerationService,
                                      NotificationService notificationService) {
        this.termRepository = termRepository;
        this.flagRepository = flagRepository;
        this.auditRepository = auditRepository;
        this.textModerationService = textModerationService;
        this.notificationService = notificationService;
    }

    // --- MOD-02: the term list -------------------------------------------------------

    @Override
    public List<ModerationTermResponse> listTerms(String query, String category, Boolean isExemption,
                                                  Boolean active, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        return termRepository.findAdmin(blankToNull(query), category, isExemption, active,
                        safeSize, safePage * safeSize).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long countTerms(String query, String category, Boolean isExemption, Boolean active) {
        return termRepository.countAdmin(blankToNull(query), category, isExemption, active);
    }

    @Override
    @Transactional
    public ModerationTermResponse createTerm(UUID actorId, UpsertModerationTermRequest request) {
        LocalDateTime now = LocalDateTime.now();
        ModerationTerm term = ModerationTerm.builder()
                .id(UUID.randomUUID())
                .term(request.getTerm().trim())
                .normalizedTerm(ModerationTextNormalizer.normalize(request.getTerm()))
                .category(request.getCategory())
                .action(request.getAction())
                .language(language(request.getLanguage()))
                .isExemption(Boolean.TRUE.equals(request.getIsExemption()))
                .note(blankToNull(request.getNote()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .dataVersion(1L)
                .createdBy(actorId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            termRepository.insert(term);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "That term already exists for this language");
        }
        audit(term.getId(), "CREATE", null, term, actorId);
        textModerationService.refresh();
        return toResponse(term);
    }

    @Override
    @Transactional
    public ModerationTermResponse updateTerm(UUID actorId, UUID id, UpsertModerationTermRequest request) {
        ModerationTerm existing = requireTerm(id);
        ModerationTerm before = copyOf(existing);

        long expectedVersion = request.getExpectedVersion() == null
                ? existing.getDataVersion()
                : request.getExpectedVersion();
        existing.setTerm(request.getTerm().trim());
        existing.setNormalizedTerm(ModerationTextNormalizer.normalize(request.getTerm()));
        existing.setCategory(request.getCategory());
        existing.setAction(request.getAction());
        existing.setLanguage(language(request.getLanguage()));
        existing.setIsExemption(Boolean.TRUE.equals(request.getIsExemption()));
        existing.setNote(blankToNull(request.getNote()));
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());
        existing.setDataVersion(expectedVersion);
        existing.setUpdatedAt(LocalDateTime.now());

        if (termRepository.update(existing) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This term was changed by another operator");
        }
        existing.setDataVersion(expectedVersion + 1);
        audit(id, "UPDATE", before, existing, actorId);
        textModerationService.refresh();
        return toResponse(existing);
    }

    @Override
    @Transactional
    public void deleteTerm(UUID actorId, UUID id) {
        ModerationTerm existing = requireTerm(id);
        audit(id, "DELETE", existing, null, actorId);
        termRepository.delete(id);
        textModerationService.refresh();
    }

    @Override
    public List<ModerationTermAuditResponse> termAudit(UUID id, int limit) {
        return termRepository.findAudit(id, Math.max(1, Math.min(limit, MAX_PAGE_SIZE))).stream()
                .map(this::toAuditResponse)
                .toList();
    }

    @Override
    public ModerationPreviewResponse preview(ModerationPreviewRequest request) {
        ModerationVisibility visibility = request.getVisibility() == null
                ? ModerationVisibility.PUBLIC
                : request.getVisibility();
        ModerationVerdict verdict = textModerationService.preview(request.getText(), visibility);
        return ModerationPreviewResponse.builder()
                .action(verdict.action())
                .category(verdict.category())
                .matchedTermId(verdict.matchedTermId())
                .matchedTerm(verdict.matchedText())
                .message(verdict.category() == null
                        ? null
                        : ErrorMessages.of("moderation.category." + verdict.category().name()))
                .build();
    }

    // --- MOD-06: the queue -----------------------------------------------------------

    @Override
    public List<ModerationFlagResponse> queue(String status, String contentType, String category,
                                              String source, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        return flagRepository.findQueue(status, contentType, category, source, safeSize, safePage * safeSize)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long countQueue(String status, String contentType, String category, String source) {
        return flagRepository.countQueue(status, contentType, category, source);
    }

    @Override
    @Transactional
    public ModerationFlagResponse resolve(UUID actorId, UUID flagId, ResolveModerationFlagRequest request) {
        ModerationFlag flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Flag not found"));
        if (request.getStatus() == ModerationFlagStatus.PENDING) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A decision must be KEPT, REMOVED or ESCALATED");
        }

        flagRepository.resolve(flagId, request.getStatus().name(), actorId, request.getNote());

        if (request.getStatus() == ModerationFlagStatus.REMOVED) {
            takeDown(flag, actorId, request.getNote());
        }
        if (request.getStatus() != ModerationFlagStatus.ESCALATED) {
            flagRepository.resolveReportsForFlag(flagId,
                    request.getStatus() == ModerationFlagStatus.REMOVED
                            ? ContentReportStatus.RESOLVED.name()
                            : ContentReportStatus.DISMISSED.name());
        }

        return flagRepository.findById(flagId).map(this::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Flag not found"));
    }

    /**
     * Removal hides the content and tells its author why. The row itself stays: an appeal
     * with nothing left to look at is not an appeal.
     */
    private void takeDown(ModerationFlag flag, UUID actorId, String note) {
        flagRepository.insertTakedown(ContentTakedown.builder()
                .id(UUID.randomUUID())
                .contentType(flag.getContentType())
                .contentId(flag.getContentId())
                .ownerId(flag.getContentOwnerId())
                .category(flag.getCategory())
                .reason(note == null || note.isBlank() ? flag.getCategory().name() : note)
                .removedBy(actorId)
                .removedAt(LocalDateTime.now())
                .build());

        if (flag.getContentOwnerId() == null) {
            return;
        }
        try {
            notificationService.createNotification(
                    flag.getContentOwnerId(),
                    null,
                    NotificationType.ADMIN_MESSAGE,
                    ErrorMessages.of(ErrorConstant.CONTENT_TAKEN_DOWN),
                    ErrorMessages.of("moderation.category." + flag.getCategory().name()),
                    Map.of("contentType", flag.getContentType().name(),
                            "contentId", flag.getContentId().toString(),
                            "category", flag.getCategory().name()),
                    actorId);
        } catch (RuntimeException exception) {
            // A silent removal creates a support ticket; a failed notification must not
            // undo a moderation decision either.
            log.warn("Could not notify {} about a takedown: {}",
                    flag.getContentOwnerId(), exception.getMessage(), exception);
        }
    }

    // --- MOD-08: the numbers ---------------------------------------------------------

    @Override
    public ModerationMetricsResponse metrics(int days) {
        LocalDateTime from = LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 365)));
        LocalDateTime to = LocalDateTime.now();

        Map<String, Object> queue = auditRepository.summarizeQueue(from, to);
        Map<String, Object> misses = auditRepository.summarizeMisses(from);

        long kept = asLong(queue.get("kept"));
        long removed = asLong(queue.get("removed"));
        long reviewed = kept + removed;
        long reported = asLong(misses.get("reported"));
        long missed = asLong(misses.get("missed_by_filter"));

        return ModerationMetricsResponse.builder()
                // A kept flag is a piece of content the filter should not have touched.
                .falsePositiveRate(reviewed == 0 ? 0d : (double) kept / reviewed)
                .missRate(reported == 0 ? 0d : (double) missed / reported)
                .pendingFlags(asLong(queue.get("pending")))
                .flagsRaised(asLong(queue.get("raised")))
                .flagsKept(kept)
                .flagsRemoved(removed)
                .averageHandlingSeconds(asDouble(queue.get("average_handling_seconds")))
                .falsePositiveTerms(auditRepository.rankFalsePositiveTerms(from, FALSE_POSITIVE_RANKING_SIZE))
                .imageCategories(auditRepository.rankImageCategories(from))
                .decisionBreakdown(auditRepository.summarizeDecisions(from, to))
                .build();
    }

    // --- helpers ---------------------------------------------------------------------

    private ModerationTerm requireTerm(UUID id) {
        return termRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Term not found"));
    }

    private void audit(UUID termId, String action, ModerationTerm before, ModerationTerm after, UUID actorId) {
        // Columns are jsonb: an empty string would be rejected, so never pass one through.
        termRepository.insertAudit(termId, action,
                before == null ? null : blankToNull(JsonUtils.toJson(before)),
                after == null ? null : blankToNull(JsonUtils.toJson(after)),
                actorId);
    }

    private ModerationTerm copyOf(ModerationTerm term) {
        return ModerationTerm.builder()
                .id(term.getId())
                .term(term.getTerm())
                .normalizedTerm(term.getNormalizedTerm())
                .category(term.getCategory())
                .action(term.getAction())
                .language(term.getLanguage())
                .isExemption(term.getIsExemption())
                .note(term.getNote())
                .isActive(term.getIsActive())
                .dataVersion(term.getDataVersion())
                .build();
    }

    private ModerationTermAuditResponse toAuditResponse(Map<String, Object> row) {
        return ModerationTermAuditResponse.builder()
                .id(asUuid(row.get("id")))
                .termId(asUuid(row.get("term_id")))
                .action(asString(row.get("action")))
                .beforeValue(asString(row.get("before_value")))
                .afterValue(asString(row.get("after_value")))
                .changedBy(asUuid(row.get("changed_by")))
                .changedAt(row.get("changed_at") instanceof LocalDateTime at ? at : null)
                .build();
    }

    private UUID asUuid(Object value) {
        return value instanceof UUID id ? id : null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private ModerationTermResponse toResponse(ModerationTerm term) {
        return ModerationTermResponse.builder()
                .id(term.getId())
                .term(term.getTerm())
                .normalizedTerm(term.getNormalizedTerm())
                .category(term.getCategory())
                .action(term.getAction())
                .language(term.getLanguage())
                .isExemption(term.getIsExemption())
                .note(term.getNote())
                .isActive(term.getIsActive())
                .dataVersion(term.getDataVersion())
                .createdAt(term.getCreatedAt())
                .updatedAt(term.getUpdatedAt())
                .build();
    }

    private ModerationFlagResponse toResponse(ModerationFlag flag) {
        return ModerationFlagResponse.builder()
                .id(flag.getId())
                .contentType(flag.getContentType())
                .contentId(flag.getContentId())
                .contentOwnerId(flag.getContentOwnerId())
                .source(flag.getSource())
                .category(flag.getCategory())
                .severity(flag.getSeverity())
                .priority(flag.getPriority())
                .reason(flag.getReason())
                .context(flag.getContextSnapshot() == null
                        ? null
                        : JsonUtils.fromJson(flag.getContextSnapshot(), Map.class))
                .reportCount(flag.getReportCount())
                .status(flag.getStatus())
                .resolutionNote(flag.getResolutionNote())
                .reviewedBy(flag.getReviewedBy())
                .reviewedAt(flag.getReviewedAt())
                .createdAt(flag.getCreatedAt())
                .takenDown(flagRepository.findActiveTakedown(
                        flag.getContentType().name(), flag.getContentId()).isPresent())
                .build();
    }

    private String language(String value) {
        return value == null || value.isBlank() ? "vi" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private double asDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0d;
    }
}
