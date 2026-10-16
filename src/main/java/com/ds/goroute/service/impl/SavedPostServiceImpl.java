package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.SavedPostPageResponse;
import com.ds.goroute.dto.response.SavedPostResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.dto.response.UserReviewResponse;
import com.ds.goroute.entity.SavedPost;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.SavedPostMapper;
import com.ds.goroute.service.ReviewService;
import com.ds.goroute.service.SavedPostService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.type.SavedPostType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SavedPostServiceImpl implements SavedPostService {

    static final int MAX_PAGE_SIZE = 50;

    private final SavedPostMapper savedPostMapper;
    private final UserCheckinService checkinService;
    private final ReviewService reviewService;
    private final UserBlockService userBlockService;

    @Override
    @Transactional
    public void save(UUID userId, SavedPostType postType, UUID postId) {
        // The same visibility rules the detail screen applies, so anything a user can open
        // they can save, and nothing else.
        UUID authorId = switch (postType) {
            case CHECKIN -> {
                UserCheckinResponse checkin = checkinService.findVisibleByIds(userId, List.of(postId)).get(postId);
                yield checkin == null ? null : checkin.getUserId();
            }
            case REVIEW -> {
                UserReviewResponse review = reviewService.findVisibleByIds(userId, List.of(postId)).get(postId);
                yield review == null ? null : review.getUserId();
            }
        };
        if (authorId == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Post not found");
        }
        if (authorId.equals(userId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "You cannot save your own post");
        }
        if (userBlockService.blockedBetween(userId, authorId)) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Post not found");
        }
        savedPostMapper.insert(userId, postType, postId);
    }

    @Override
    @Transactional
    public void unsave(UUID userId, SavedPostType postType, UUID postId) {
        // No visibility check: a post that has since gone must still be removable.
        savedPostMapper.delete(userId, postType, postId);
    }

    @Override
    @Transactional(readOnly = true)
    public SavedPostPageResponse list(UUID userId, boolean oldestFirst, String cursor, int size) {
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Cursor after = Cursor.decode(cursor);
        List<SavedPost> rows = savedPostMapper.findPage(userId, oldestFirst,
                after == null ? null : after.createdAt(),
                after == null ? null : after.postType().name(),
                after == null ? null : after.postId(),
                limit + 1);
        boolean hasMore = rows.size() > limit;
        List<SavedPost> page = hasMore ? rows.subList(0, limit) : rows;

        List<UUID> checkinIds = idsOf(page, SavedPostType.CHECKIN);
        List<UUID> reviewIds = idsOf(page, SavedPostType.REVIEW);
        Map<UUID, UserCheckinResponse> checkins = checkinIds.isEmpty()
                ? Map.of() : checkinService.findVisibleByIds(userId, checkinIds);
        Map<UUID, UserReviewResponse> reviews = reviewIds.isEmpty()
                ? Map.of() : reviewService.findVisibleByIds(userId, reviewIds);

        Set<UUID> authorIds = new HashSet<>();
        checkins.values().forEach(checkin -> authorIds.add(checkin.getUserId()));
        reviews.values().forEach(review -> authorIds.add(review.getUserId()));
        Set<UUID> blocked = authorIds.isEmpty() ? Set.of() : userBlockService.blockedAmong(userId, authorIds);

        List<SavedPostResponse> items = page.stream()
                .map(row -> toResponse(row, checkins, reviews, blocked))
                .toList();
        SavedPost last = page.isEmpty() ? null : page.get(page.size() - 1);
        return SavedPostPageResponse.builder()
                .items(items)
                .hasMore(hasMore)
                .nextCursor(hasMore && last != null ? new Cursor(last.getCreatedAt(), last.getPostType(),
                        last.getPostId()).encode() : null)
                .totalCount(savedPostMapper.countByUser(userId))
                .build();
    }

    private static SavedPostResponse toResponse(SavedPost row,
                                                Map<UUID, UserCheckinResponse> checkins,
                                                Map<UUID, UserReviewResponse> reviews,
                                                Set<UUID> blocked) {
        UserCheckinResponse checkin = row.getPostType() == SavedPostType.CHECKIN
                ? checkins.get(row.getPostId()) : null;
        UserReviewResponse review = row.getPostType() == SavedPostType.REVIEW
                ? reviews.get(row.getPostId()) : null;
        UUID authorId = checkin != null ? checkin.getUserId() : review != null ? review.getUserId() : null;
        boolean available = authorId != null && !blocked.contains(authorId);
        return SavedPostResponse.builder()
                .postType(row.getPostType())
                .postId(row.getPostId())
                .savedAt(row.getCreatedAt())
                .available(available)
                .checkin(available ? checkin : null)
                .review(available ? review : null)
                .build();
    }

    private static List<UUID> idsOf(List<SavedPost> rows, SavedPostType type) {
        return rows.stream()
                .filter(row -> row.getPostType() == type)
                .map(SavedPost::getPostId)
                .toList();
    }

    /**
     * Where a page ended: the sort key of its last save. Opaque on the wire, URL-safe Base64 of
     * {@code 2026-10-14T09:15:02.123456|CHECKIN|uuid}; the order direction is not in it, so the
     * client must ask for the next page with the same {@code sort}.
     */
    record Cursor(LocalDateTime createdAt, SavedPostType postType, UUID postId) {

        private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
        private static final int MAX_ENCODED_LENGTH = 200;

        String encode() {
            String raw = TIMESTAMP.format(createdAt) + "|" + postType.name() + "|" + postId;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        }

        static Cursor decode(String encoded) {
            if (encoded == null || encoded.isBlank()) {
                return null;
            }
            if (encoded.length() > MAX_ENCODED_LENGTH) {
                throw invalid();
            }
            try {
                String raw = new String(Base64.getUrlDecoder().decode(encoded.trim()), StandardCharsets.UTF_8);
                String[] parts = raw.split("\\|", -1);
                if (parts.length != 3) {
                    throw invalid();
                }
                return new Cursor(LocalDateTime.parse(parts[0], TIMESTAMP),
                        SavedPostType.valueOf(parts[1]),
                        UUID.fromString(parts[2]));
            } catch (IllegalArgumentException | DateTimeParseException e) {
                throw invalid();
            }
        }

        private static BusinessException invalid() {
            return new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid saved post cursor");
        }
    }
}
