package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateContentCommentRequest;
import com.ds.goroute.dto.request.UpdateContentCommentRequest;
import com.ds.goroute.dto.response.ContentCommentPageResponse;
import com.ds.goroute.dto.response.ContentCommentResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.entity.PlaceCollection;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PlaceCollectionMapper;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.repository.UserReviewRepository;
import com.ds.goroute.service.ContentCommentService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.TripVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Owns comments on the four social targets.  Visibility is decided here, before both a
 * read and a write, so a private check-in or collection cannot accidentally gain a
 * discussion thread through a new endpoint.
 */
@Service
@RequiredArgsConstructor
public class ContentCommentServiceImpl implements ContentCommentService {
    private final ContentCommentRepository commentRepository;
    private final TripRepository tripRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserCheckinRepository checkinRepository;
    private final UserReviewRepository reviewRepository;
    private final PlaceCollectionMapper collectionMapper;
    private final UserRepository userRepository;
    private final ContentModerationService contentModerationService;
    private final SocialNotificationService socialNotificationService;

    @Override
    @Transactional(readOnly = true)
    public ContentCommentPageResponse getComments(ModeratedContentType contentType, UUID contentId, UUID parentId,
                                                  String cursor, int limit, UUID viewerId) {
        verifyTargetReadable(contentType, contentId, viewerId);
        if (parentId != null) {
            verifyParentBelongs(parentId, contentType, contentId);
        }
        CursorPosition after = decodeCursor(cursor);
        int pageSize = Math.max(1, Math.min(limit, 50));
        List<ContentComment> fetched = commentRepository.findPage(
                contentType, contentId, parentId,
                after == null ? null : after.createdAt(), after == null ? null : after.id(),
                pageSize + 1, viewerId);
        boolean hasMore = fetched.size() > pageSize;
        List<ContentComment> comments = hasMore ? fetched.subList(0, pageSize) : fetched;
        Set<UUID> takenDownIds = contentModerationService.takenDownIds(
                ModeratedContentType.CONTENT_COMMENT,
                comments.stream().map(ContentComment::getId).toList());
        List<ContentCommentResponse> responses = comments.stream().map(comment -> toResponse(comment,
                Boolean.TRUE.equals(comment.getIsDeleted()) || takenDownIds.contains(comment.getId()))).toList();
        return ContentCommentPageResponse.builder()
                .comments(responses)
                .hasMore(hasMore)
                .nextCursor(hasMore ? encodeCursor(comments.get(comments.size() - 1)) : null)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public int getActiveCommentCount(ModeratedContentType contentType, UUID contentId, UUID viewerId) {
        verifyTargetReadable(contentType, contentId, viewerId);
        List<ContentComment> comments = commentRepository.findByTarget(contentType, contentId);
        Set<UUID> takenDownIds = contentModerationService.takenDownIds(
                ModeratedContentType.CONTENT_COMMENT,
                comments.stream().map(ContentComment::getId).toList());
        return (int) comments.stream()
                .filter(comment -> !Boolean.TRUE.equals(comment.getIsDeleted()))
                .filter(comment -> !takenDownIds.contains(comment.getId()))
                .count();
    }

    @Override
    @Transactional
    public ContentCommentResponse createComment(CreateContentCommentRequest request, UUID userId) {
        verifyTargetReadable(request.getContentType(), request.getContentId(), userId);
        verifyParent(request.getParentId(), request.getContentType(), request.getContentId());

        LocalDateTime now = LocalDateTime.now();
        ContentComment comment = ContentComment.builder()
                .id(UUID.randomUUID())
                .contentType(request.getContentType())
                .contentId(request.getContentId())
                .parentId(request.getParentId())
                .userId(userId)
                .content(request.getContent().trim())
                .isDeleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        commentRepository.insert(comment);
        if (request.getParentId() != null) {
            commentRepository.findById(request.getParentId()).ifPresent(parent ->
                    socialNotificationService.notifyComment(
                            parent.getUserId(), userId,
                            ModeratedContentType.CONTENT_COMMENT.name(), parent.getId()));
        } else {
            ownerOf(request.getContentType(), request.getContentId()).ifPresent(ownerId ->
                    socialNotificationService.notifyComment(
                            ownerId, userId, request.getContentType().name(), request.getContentId()));
        }
        return toResponse(comment, false);
    }

    @Override
    @Transactional
    public ContentCommentResponse updateComment(UUID commentId, UpdateContentCommentRequest request, UUID userId) {
        ContentComment comment = ownEditableComment(commentId, userId);
        commentRepository.updateContent(commentId, request.getContent().trim());
        comment.setContent(request.getContent().trim());
        comment.setUpdatedAt(LocalDateTime.now());
        return toResponse(comment, false);
    }

    @Override
    @Transactional
    public ContentCommentResponse toggleLike(UUID commentId, UUID userId) {
        ContentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Comment not found"));
        verifyTargetReadable(comment.getContentType(), comment.getContentId(), userId);
        if (Boolean.TRUE.equals(comment.getIsDeleted()) || contentModerationService.isTakenDown(
                ModeratedContentType.CONTENT_COMMENT, commentId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Comment not found");
        }
        if (commentRepository.hasLike(commentId, userId)) {
            commentRepository.deleteLike(commentId, userId);
        } else {
            commentRepository.insertLike(commentId, userId);
            socialNotificationService.notifyLike(
                    comment.getUserId(), userId, ModeratedContentType.CONTENT_COMMENT.name(), commentId);
        }
        ContentComment refreshed = commentRepository.findByIdWithStats(commentId, userId)
                .orElse(comment);
        return toResponse(refreshed, false);
    }

    @Override
    @Transactional
    public void deleteComment(UUID commentId, UUID userId) {
        ContentComment comment = ownEditableComment(commentId, userId);
        commentRepository.softDelete(commentId);
    }

    private ContentComment ownEditableComment(UUID commentId, UUID userId) {
        ContentComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Comment not found"));
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN, "You can only edit or delete your own comments");
        }
        if (Boolean.TRUE.equals(comment.getIsDeleted())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Comment has been deleted");
        }
        return comment;
    }

    private CursorPosition decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            return new CursorPosition(LocalDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid comment cursor");
        }
    }

    private String encodeCursor(ContentComment comment) {
        String raw = comment.getCreatedAt() + "|" + comment.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private record CursorPosition(LocalDateTime createdAt, UUID id) {
    }

    private void verifyParent(UUID parentId, ModeratedContentType contentType, UUID contentId) {
        if (parentId == null) {
            return;
        }
        ContentComment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Parent comment not found"));
        verifyParentBelongs(parent, contentType, contentId);
        if (Boolean.TRUE.equals(parent.getIsDeleted()) || contentModerationService.isTakenDown(
                ModeratedContentType.CONTENT_COMMENT, parentId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "You cannot reply to a removed comment");
        }
    }

    private void verifyParentBelongs(UUID parentId, ModeratedContentType contentType, UUID contentId) {
        ContentComment parent = commentRepository.findById(parentId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Parent comment not found"));
        verifyParentBelongs(parent, contentType, contentId);
    }

    private void verifyParentBelongs(ContentComment parent, ModeratedContentType contentType, UUID contentId) {
        if (parent.getContentType() != contentType || !parent.getContentId().equals(contentId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A reply must belong to the same content thread");
        }
    }

    private void verifyTargetReadable(ModeratedContentType contentType, UUID contentId, UUID viewerId) {
        if (!isCommentableType(contentType)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Comments are only available for trips, check-ins, reviews and public collections");
        }
        switch (contentType) {
            case TRIP -> verifyTripReadable(contentId, viewerId);
            case CHECKIN -> verifyPublicCheckin(contentId);
            case REVIEW -> reviewRepository.findById(contentId)
                    .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Review not found"));
            case PLACE_COLLECTION -> verifyPublicCollection(contentId);
            default -> throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Unsupported comment target");
        }
    }

    private boolean isCommentableType(ModeratedContentType contentType) {
        return contentType == ModeratedContentType.TRIP
                || contentType == ModeratedContentType.CHECKIN
                || contentType == ModeratedContentType.REVIEW
                || contentType == ModeratedContentType.PLACE_COLLECTION;
    }

    private java.util.Optional<UUID> ownerOf(ModeratedContentType contentType, UUID contentId) {
        return switch (contentType) {
            case TRIP -> tripRepository.findById(contentId).map(Trip::getOwnerId);
            case CHECKIN -> checkinRepository.findById(contentId).map(UserCheckin::getUserId);
            case REVIEW -> reviewRepository.findById(contentId).map(review -> review.getUserId());
            case PLACE_COLLECTION -> java.util.Optional.ofNullable(collectionMapper.findById(contentId))
                    .map(PlaceCollection::getOwnerId);
            default -> java.util.Optional.empty();
        };
    }

    private void verifyTripReadable(UUID tripId, UUID viewerId) {
        Trip trip = tripRepository.findById(tripId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found"));
        if (Boolean.TRUE.equals(trip.getIsDeleted())
                || contentModerationService.isTakenDown(ModeratedContentType.TRIP, tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Trip not found");
        }
        if (trip.getVisibility() == TripVisibility.PUBLIC) {
            return;
        }
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, viewerId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip"));
        if (member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip");
        }
    }

    private void verifyPublicCheckin(UUID checkinId) {
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        if (checkin.getVisibility() != ContentVisibility.PUBLIC || Boolean.TRUE.equals(checkin.getIsRemoved())
                || contentModerationService.isTakenDown(ModeratedContentType.CHECKIN, checkinId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found");
        }
    }

    private void verifyPublicCollection(UUID collectionId) {
        PlaceCollection collection = collectionMapper.findById(collectionId);
        if (collection == null || collection.getVisibility() != ContentVisibility.PUBLIC
                || Boolean.TRUE.equals(collection.getIsRemoved()) || contentModerationService.isTakenDown(
                ModeratedContentType.PLACE_COLLECTION, collectionId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Collection not found");
        }
    }

    private ContentCommentResponse toResponse(ContentComment comment, boolean removed) {
        User user = userRepository.findById(comment.getUserId()).orElse(null);
        return ContentCommentResponse.builder()
                .id(comment.getId())
                .contentType(comment.getContentType())
                .contentId(comment.getContentId())
                .parentId(comment.getParentId())
                .user(user == null ? null : UserResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .content(removed ? null : comment.getContent())
                .isDeleted(removed)
                .replyCount(comment.getReplyCount() == null ? 0 : comment.getReplyCount())
                .likeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount())
                .hasLiked(Boolean.TRUE.equals(comment.getHasLiked()))
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
