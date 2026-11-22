package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.FeedFeedbackRequest;
import com.ds.goroute.dto.response.FeedFeedbackResponse;
import com.ds.goroute.entity.FeedFeedback;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.FeedFeedbackMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FeedFeedbackService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.FeedFeedbackKind;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FeedFeedbackServiceImpl implements FeedFeedbackService {

    /** Revoked and expired rows are kept this long, then removed by the night job. */
    static final int FINISHED_KEEP_DAYS = 90;
    static final int PURGE_SLICE = 5_000;

    private final FeedFeedbackMapper feedbackMapper;
    private final UserRepository userRepository;
    private final BusinessConfigService config;

    @Override
    @Transactional
    public FeedFeedbackResponse hide(UUID userId, FeedFeedbackRequest request) {
        FeedFeedback feedback = switch (request.getKind()) {
            case HIDE_POST -> {
                if (request.getItemType() == null || request.getItemId() == null) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "itemType and itemId are required");
                }
                yield FeedFeedback.builder()
                        .kind(FeedFeedbackKind.HIDE_POST)
                        .itemType(request.getItemType())
                        .itemId(request.getItemId())
                        .build();
            }
            case HIDE_AUTHOR -> {
                UUID target = request.getTargetUserId();
                if (target == null) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "targetUserId is required");
                }
                if (target.equals(userId)) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "You cannot hide yourself");
                }
                if (userRepository.findById(target).isEmpty()) {
                    throw new BusinessException(ErrorConstant.NOT_FOUND, "User not found");
                }
                yield FeedFeedback.builder()
                        .kind(FeedFeedbackKind.HIDE_AUTHOR)
                        .targetUserId(target)
                        .expiresAt(LocalDateTime.now().plusDays(config.getInt(BusinessConfigKey.FEED_HIDE_AUTHOR_DAYS)))
                        .build();
            }
        };
        feedback.setUserId(userId);

        FeedFeedback existing = feedbackMapper.findActiveSame(feedback);
        if (existing != null) {
            return toResponse(existing);
        }
        feedback.setId(UUID.randomUUID());
        feedbackMapper.insert(feedback);
        return toResponse(feedback);
    }

    @Override
    @Transactional
    public void unhide(UUID userId, UUID feedbackId) {
        feedbackMapper.revoke(feedbackId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Hidden hiddenFor(UUID viewerId) {
        if (viewerId == null) {
            return Hidden.NONE;
        }
        List<FeedFeedback> active = feedbackMapper.findActive(viewerId);
        if (active.isEmpty()) {
            return Hidden.NONE;
        }
        Set<String> posts = new HashSet<>();
        Set<UUID> authors = new HashSet<>();
        for (FeedFeedback feedback : active) {
            if (feedback.getKind() == FeedFeedbackKind.HIDE_POST) {
                posts.add(Hidden.key(feedback.getItemType(), feedback.getItemId()));
            } else {
                authors.add(feedback.getTargetUserId());
            }
        }
        return new Hidden(Set.copyOf(posts), Set.copyOf(authors));
    }

    @Override
    public int purgeFinished() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(FINISHED_KEEP_DAYS);
        int total = 0;
        int deleted;
        do {
            deleted = feedbackMapper.deleteFinishedBefore(cutoff, PURGE_SLICE);
            total += deleted;
        } while (deleted == PURGE_SLICE);
        return total;
    }

    private static FeedFeedbackResponse toResponse(FeedFeedback feedback) {
        return FeedFeedbackResponse.builder()
                .id(feedback.getId())
                .kind(feedback.getKind())
                .expiresAt(feedback.getExpiresAt())
                .build();
    }
}
