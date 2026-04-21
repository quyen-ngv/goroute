package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateCommentRequest;
import com.ds.goroute.dto.response.CommentResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.ActivityComment;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityCommentRepository;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.CommentService;
import com.ds.goroute.service.notification.NotificationHelper;
import com.ds.goroute.type.MemberStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {
    
    private final ActivityCommentRepository commentRepository;
    private final ActivityRepository activityRepository;
    private final TripMemberRepository tripMemberRepository;
    private final UserRepository userRepository;
    private final NotificationHelper notificationHelper;

    @Override
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(UUID tripId, UUID activityId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        
        // Verify activity belongs to trip
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        
        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }
        
        List<ActivityComment> comments = commentRepository.findByActivityId(activityId);
        
        return comments.stream()
                .map(this::toCommentResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public CommentResponse createComment(UUID tripId, UUID activityId, CreateCommentRequest request, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        
        // Verify activity belongs to trip
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        
        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }
        
        ActivityComment comment = ActivityComment.builder()
                .id(UUID.randomUUID())
                .activityId(activityId)
                .userId(userId)
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .isDeleted(false)
                .build();
        
        commentRepository.insert(comment);
        log.info("Comment created: {} for activity: {}", comment.getId(), activityId);
        
        notificationHelper.emitCommentCreated(tripId, activityId, userId);
        
        return toCommentResponse(comment);
    }

    @Override
    @Transactional
    public void deleteComment(UUID tripId, UUID activityId, UUID commentId, UUID userId) {
        // Verify user is member of trip
        verifyTripMember(tripId, userId);
        
        // Verify activity belongs to trip
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found"));
        
        if (!activity.getTripId().equals(tripId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Activity not found");
        }
        
        ActivityComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Comment not found"));
        
        // Only comment owner can delete
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN, "You can only delete your own comments");
        }
        
        commentRepository.softDelete(commentId);
        log.info("Comment deleted: {}", commentId);
        
        notificationHelper.emitCommentDeleted(tripId, activityId, userId);
    }
    
    private void verifyTripMember(UUID tripId, UUID userId) {
        TripMember member = tripMemberRepository.findByTripIdAndUserId(tripId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip"));
        
        if (member.getStatus() != MemberStatus.ACCEPTED) {
            throw new BusinessException(ErrorConstant.FORBIDDEN, "You are not a member of this trip");
        }
    }
    
    private CommentResponse toCommentResponse(ActivityComment comment) {
        User user = userRepository.findById(comment.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));
        
        return CommentResponse.builder()
                .id(comment.getId())
                .activityId(comment.getActivityId())
                .user(UserResponse.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .avatarUrl(user.getAvatarUrl())
                        .build())
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
