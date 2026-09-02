package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ReportContentRequest;
import com.ds.goroute.dto.response.ContentReportResponse;
import com.ds.goroute.entity.ContentReport;
import com.ds.goroute.entity.ActivityComment;
import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.entity.ModerationFlag;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.repository.ActivityCommentRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.ContentReportStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationCategory;
import com.ds.goroute.type.ModerationFlagSource;
import com.ds.goroute.type.ModerationFlagStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentModerationServiceImpl implements ContentModerationService {

    private final ModerationFlagRepository flagRepository;
    private final ContentCommentRepository contentCommentRepository;
    private final ActivityCommentRepository activityCommentRepository;

    @Override
    @Transactional
    public void flag(ModeratedContentType contentType,
                     UUID contentId,
                     UUID ownerId,
                     ModerationFlagSource source,
                     ModerationVerdict verdict,
                     String contextSnapshot) {
        if (contentId == null || verdict == null || verdict.isAllowed()) {
            return;
        }
        ModerationCategory category = verdict.category();
        flagRepository.upsert(ModerationFlag.builder()
                .id(UUID.randomUUID())
                .contentType(contentType)
                .contentId(contentId)
                .contentOwnerId(ownerId)
                .source(source)
                .category(category)
                .severity(category.severity())
                .priority(category.priority())
                .reason(verdict.matchedText())
                .contextSnapshot(contextSnapshot)
                .reportCount(source == ModerationFlagSource.USER_REPORT ? 1 : 0)
                .status(ModerationFlagStatus.PENDING)
                .build());
    }

    @Override
    @Transactional
    public ContentReportResponse report(UUID reporterId, ReportContentRequest request) {
        ModerationCategory category = request.getReason().category();
        ContentReportMetadata metadata = reportMetadata(request);
        // The report raises a flag first so that the report row can point at it and the
        // queue shows one item per (content, category) no matter how many people report.
        flag(request.getContentType(),
                request.getContentId(),
                metadata.ownerId(),
                ModerationFlagSource.USER_REPORT,
                ModerationVerdict.of(com.ds.goroute.type.ModerationAction.FLAG, category, null,
                        request.getReason().name()),
                metadata.contextSnapshot());

        UUID flagId = flagRepository.findOpen(
                request.getContentType().name(),
                request.getContentId(),
                ModerationFlagSource.USER_REPORT.name(),
                category.name()).map(ModerationFlag::getId).orElse(null);

        ContentReport report = ContentReport.builder()
                .id(UUID.randomUUID())
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .reporterId(reporterId)
                .reason(request.getReason())
                .note(request.getNote())
                .flagId(flagId)
                .status(ContentReportStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        try {
            flagRepository.insertReport(report);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ErrorConstant.CONTENT_ALREADY_REPORTED,
                    "You have already reported this content.");
        }
        return ContentReportResponse.builder()
                .id(report.getId())
                .contentType(report.getContentType())
                .contentId(report.getContentId())
                .reason(report.getReason())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .build();
    }

    /**
     * A generic report normally only carries an id. A comment is short standalone text,
     * so the reviewer needs its exact context and a removal must notify its author.
     *
     * <p>Activity comments are included for the same reason. Without the snapshot a moderator sees
     * a queue entry with nothing to judge, which is how a report can be filed and then never
     * actioned -- the takedown that follows is what makes the report worth filing at all.
     */
    private ContentReportMetadata reportMetadata(ReportContentRequest request) {
        return switch (request.getContentType()) {
            case CONTENT_COMMENT -> {
                ContentComment comment = contentCommentRepository.findById(request.getContentId())
                        .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Comment not found"));
                if (Boolean.TRUE.equals(comment.getIsDeleted())) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                            "Comment is no longer available");
                }
                yield new ContentReportMetadata(comment.getUserId(), comment.getContent());
            }
            case ACTIVITY_COMMENT -> {
                ActivityComment comment = activityCommentRepository.findById(request.getContentId())
                        .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Comment not found"));
                if (Boolean.TRUE.equals(comment.getIsDeleted())) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                            "Comment is no longer available");
                }
                yield new ContentReportMetadata(comment.getUserId(), comment.getContent());
            }
            default -> new ContentReportMetadata(null, null);
        };
    }

    private record ContentReportMetadata(UUID ownerId, String contextSnapshot) {
    }

    @Override
    public boolean isTakenDown(ModeratedContentType contentType, UUID contentId) {
        return contentId != null
                && flagRepository.findActiveTakedown(contentType.name(), contentId).isPresent();
    }

    @Override
    public Set<UUID> takenDownIds(ModeratedContentType contentType, Collection<UUID> contentIds) {
        if (contentIds == null || contentIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> distinct = contentIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(flagRepository.findActiveTakedownIds(contentType.name(), distinct));
    }
}
