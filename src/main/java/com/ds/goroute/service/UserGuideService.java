package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.UserGuideGrant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.UserGuideMapper;
import com.ds.goroute.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Who is a VietdeGuide.
 *
 * <p>A guide is an account an operator has vouched for. That is all this decides. What a guide is
 * then allowed to <em>do</em> -- run tours, manage listings -- comes from the partner organisation
 * they belong to and its GUIDE role, which already exists and is checked where it is used. Keeping
 * the two apart is deliberate: the badge is about the person, the permissions are about the
 * business they work for, and one changing should not silently change the other.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserGuideService {

    private final UserGuideMapper mapper;
    private final UserRepository users;

    @Transactional(readOnly = true)
    public Optional<UserGuideGrant> find(UUID userId) {
        return Optional.ofNullable(mapper.find(userId)).map(this::withAreas);
    }

    @Transactional(readOnly = true)
    public boolean isGuide(UUID userId) {
        UserGuideGrant grant = userId == null ? null : mapper.find(userId);
        return grant != null && grant.isActive();
    }

    /** Which of these accounts are guides, in one query, for badging a list. */
    @Transactional(readOnly = true)
    public Set<UUID> activeGuideIds(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> distinct = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (distinct.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(mapper.findActiveIds(distinct));
    }

    /**
     * Makes this account a guide, or restores one that was revoked.
     *
     * <p>Idempotent: granting twice leaves the same single row, with the second grant's title and
     * operator on it.
     */
    @Transactional
    public UserGuideGrant grant(UUID userId, String displayTitle, String note,
                                List<UUID> locationImageIds, UUID operatorId) {
        // Checked rather than left to a foreign key, which this table deliberately does not have:
        // the failure would otherwise reach the operator as a constraint name.
        users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        mapper.upsertActive(UserGuideGrant.builder()
                .userId(userId)
                .displayTitle(trimToNull(displayTitle))
                .note(trimToNull(note))
                .grantedBy(operatorId)
                .build());
        // Null means "leave the areas as they are"; an empty list is an explicit clear, so the
        // two cannot be collapsed into one branch.
        if (locationImageIds != null) {
            mapper.deleteLocationImages(userId);
            locationImageIds.stream().filter(Objects::nonNull).distinct()
                    .forEach(areaId -> mapper.insertLocationImage(userId, areaId));
        }
        log.info("User {} was made a guide by {}", userId, operatorId);
        return withAreas(mapper.find(userId));
    }

    /**
     * Ends someone's guide status, keeping the row so the history stays readable.
     *
     * @throws BusinessException 404 when this account is not currently a guide, so a double
     *                           revocation reads as "already done" rather than silently succeeding
     */
    @Transactional
    public UserGuideGrant revoke(UUID userId, String reason, UUID operatorId) {
        if (mapper.revoke(userId, operatorId, trimToNull(reason)) == 0) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "This account is not a guide");
        }
        log.info("Guide status for user {} was revoked by {}", userId, operatorId);
        // The area rows stay: restoring a guide should not silently lose which areas they covered.
        return withAreas(mapper.find(userId));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(String status, String search, int limit, int offset) {
        return mapper.findAll(trimToNull(status), trimToNull(search), limit, offset);
    }

    @Transactional(readOnly = true)
    public long count(String status, String search) {
        return mapper.countAll(trimToNull(status), trimToNull(search));
    }

    private UserGuideGrant withAreas(UserGuideGrant grant) {
        if (grant != null) {
            grant.setLocationImageIds(mapper.findLocationImageIds(grant.getUserId()));
        }
        return grant;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
