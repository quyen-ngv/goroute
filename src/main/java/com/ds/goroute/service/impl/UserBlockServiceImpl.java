package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.UserBlockResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.UserBlock;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.UserBlockMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.UserBlockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserBlockServiceImpl implements UserBlockService {

    /** A reason is for the blocker's own memory; anything longer is a report, not a note. */
    private static final int MAX_REASON_LENGTH = 500;

    private final UserBlockMapper blocks;
    private final UserRepository users;

    @Override
    @Transactional
    public UserBlockResponse block(UUID blockerId, UUID blockedId, String reason) {
        if (blockerId.equals(blockedId)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "You cannot block yourself");
        }
        User blocked = users.findById(blockedId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        blocks.insert(UserBlock.builder()
                .blockerId(blockerId)
                .blockedId(blockedId)
                .reason(trimmed(reason))
                .build());

        return toResponse(blocked, trimmed(reason), null);
    }

    @Override
    @Transactional
    public void unblock(UUID blockerId, UUID blockedId) {
        // Unblocking somebody who was never blocked is not an error: the caller wanted a
        // state, and the state is already what they wanted.
        blocks.delete(blockerId, blockedId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBlockResponse> listBlocked(UUID blockerId) {
        List<UserBlock> rows = blocks.selectByBlocker(blockerId);
        // One lookup per row, but the list is a person's own blocks and is short by nature;
        // batching here would add a mapper method to save nothing measurable.
        return rows.stream()
                .map(row -> users.findById(row.getBlockedId())
                        .map(user -> toResponse(user, row.getReason(), row.getCreatedAt()))
                        // A blocked account that has since been deleted still shows, so the
                        // count matches and the entry can be removed.
                        .orElseGet(() -> UserBlockResponse.builder()
                                .userId(row.getBlockedId())
                                .reason(row.getReason())
                                .createdAt(row.getCreatedAt())
                                .build()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean blockedBetween(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) return false;
        return blocks.existsBetween(first, second);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> blockedAmong(UUID viewer, Collection<UUID> candidates) {
        if (viewer == null || candidates == null || candidates.isEmpty()) return Set.of();
        Set<UUID> distinct = new HashSet<>(candidates);
        distinct.remove(viewer);
        if (distinct.isEmpty()) return Set.of();
        return new HashSet<>(blocks.selectBlockedAmong(viewer, distinct));
    }

    private UserBlockResponse toResponse(User user, String reason, java.time.LocalDateTime createdAt) {
        return UserBlockResponse.builder()
                .userId(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .reason(reason)
                .createdAt(createdAt)
                .build();
    }

    private String trimmed(String reason) {
        if (reason == null || reason.isBlank()) return null;
        String value = reason.trim();
        return value.length() > MAX_REASON_LENGTH ? value.substring(0, MAX_REASON_LENGTH) : value;
    }
}
