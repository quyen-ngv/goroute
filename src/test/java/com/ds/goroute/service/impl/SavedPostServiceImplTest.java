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
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.type.SavedPostType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Saving other people's posts (epic 06, §2.6.2). */
class SavedPostServiceImplTest {

    private final SavedPostMapper mapper = mock(SavedPostMapper.class);
    private final UserCheckinService checkins = mock(UserCheckinService.class);
    private final ReviewService reviews = mock(ReviewService.class);
    private final UserBlockService blocks = mock(UserBlockService.class);
    private final SavedPostServiceImpl service = new SavedPostServiceImpl(mapper, checkins, reviews, blocks);

    private final UUID viewer = UUID.randomUUID();
    private final UUID author = UUID.randomUUID();

    @Test
    @DisplayName("Saves a visible check-in written by someone else")
    void savesVisibleCheckin() {
        UUID checkinId = UUID.randomUUID();
        when(checkins.findVisibleByIds(viewer, List.of(checkinId)))
                .thenReturn(Map.of(checkinId, checkin(checkinId, author)));

        service.save(viewer, SavedPostType.CHECKIN, checkinId);

        verify(mapper).insert(viewer, SavedPostType.CHECKIN, checkinId);
    }

    @Test
    @DisplayName("Refuses a post the viewer cannot open with 404")
    void refusesInvisiblePost() {
        UUID reviewId = UUID.randomUUID();
        when(reviews.findVisibleByIds(viewer, List.of(reviewId))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.save(viewer, SavedPostType.REVIEW, reviewId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getError().getCode())
                .isEqualTo(ErrorConstant.NOT_FOUND);
        verify(mapper, never()).insert(any(), any(), any());
    }

    @Test
    @DisplayName("Refuses the viewer's own post with 400")
    void refusesOwnPost() {
        UUID reviewId = UUID.randomUUID();
        when(reviews.findVisibleByIds(viewer, List.of(reviewId)))
                .thenReturn(Map.of(reviewId, review(reviewId, viewer)));

        assertThatThrownBy(() -> service.save(viewer, SavedPostType.REVIEW, reviewId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getError().getCode())
                .isEqualTo(ErrorConstant.INVALID_PARAMETERS);
        verify(mapper, never()).insert(any(), any(), any());
    }

    @Test
    @DisplayName("Refuses a post whose author is blocked either way, as if it did not exist")
    void refusesBlockedAuthor() {
        UUID checkinId = UUID.randomUUID();
        when(checkins.findVisibleByIds(viewer, List.of(checkinId)))
                .thenReturn(Map.of(checkinId, checkin(checkinId, author)));
        when(blocks.blockedBetween(viewer, author)).thenReturn(true);

        assertThatThrownBy(() -> service.save(viewer, SavedPostType.CHECKIN, checkinId))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getError().getCode())
                .isEqualTo(ErrorConstant.NOT_FOUND);
    }

    @Test
    @DisplayName("Lists live posts in save order and marks gone or blocked ones unavailable")
    void listsWithAvailability() {
        UUID live = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        UUID liveReview = UUID.randomUUID();
        UUID blockedAuthor = UUID.randomUUID();
        UUID blockedReview = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 14, 9, 0);
        when(mapper.findPage(eq(viewer), eq(false), isNull(), isNull(), isNull(), eq(21))).thenReturn(List.of(
                row(SavedPostType.CHECKIN, live, now),
                row(SavedPostType.CHECKIN, deleted, now.minusMinutes(1)),
                row(SavedPostType.REVIEW, liveReview, now.minusMinutes(2)),
                row(SavedPostType.REVIEW, blockedReview, now.minusMinutes(3))));
        when(checkins.findVisibleByIds(viewer, List.of(live, deleted)))
                .thenReturn(Map.of(live, checkin(live, author)));
        when(reviews.findVisibleByIds(viewer, List.of(liveReview, blockedReview)))
                .thenReturn(Map.of(liveReview, review(liveReview, author),
                        blockedReview, review(blockedReview, blockedAuthor)));
        when(blocks.blockedAmong(eq(viewer), any())).thenReturn(Set.of(blockedAuthor));
        when(mapper.countByUser(viewer)).thenReturn(4);

        SavedPostPageResponse page = service.list(viewer, false, null, 20);

        assertThat(page.getItems())
                .extracting(SavedPostResponse::getPostId, SavedPostResponse::isAvailable)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(live, true),
                        org.assertj.core.groups.Tuple.tuple(deleted, false),
                        org.assertj.core.groups.Tuple.tuple(liveReview, true),
                        org.assertj.core.groups.Tuple.tuple(blockedReview, false));
        assertThat(page.getItems().get(3).getReview()).as("a blocked author's post is not sent").isNull();
        assertThat(page.isHasMore()).isFalse();
        assertThat(page.getNextCursor()).isNull();
        assertThat(page.getTotalCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("The next cursor resumes after the last row of the page")
    void cursorRoundTrips() {
        LocalDateTime base = LocalDateTime.of(2026, 10, 14, 9, 0, 0, 123_456_000);
        List<SavedPost> rows = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            rows.add(row(SavedPostType.CHECKIN, UUID.randomUUID(), base.minusSeconds(i)));
        }
        when(mapper.findPage(eq(viewer), anyBoolean(), any(), any(), any(), anyInt())).thenReturn(rows);
        when(checkins.findVisibleByIds(eq(viewer), any())).thenReturn(Map.of());

        SavedPostPageResponse first = service.list(viewer, true, null, 2);
        assertThat(first.isHasMore()).isTrue();
        assertThat(first.getItems()).hasSize(2);

        service.list(viewer, true, first.getNextCursor(), 2);

        SavedPost last = rows.get(1);
        verify(mapper).findPage(viewer, true, last.getCreatedAt(), "CHECKIN", last.getPostId(), 3);
    }

    @Test
    @DisplayName("A cursor this server did not issue is a 400")
    void rejectsForeignCursor() {
        assertThatThrownBy(() -> service.list(viewer, false, "not-a-cursor", 20))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).getError().getCode())
                .isEqualTo(ErrorConstant.INVALID_PARAMETERS);
    }

    private static SavedPost row(SavedPostType type, UUID id, LocalDateTime createdAt) {
        return SavedPost.builder().postType(type).postId(id).createdAt(createdAt).build();
    }

    private static UserCheckinResponse checkin(UUID id, UUID userId) {
        return UserCheckinResponse.builder().id(id).userId(userId).build();
    }

    private static UserReviewResponse review(UUID id, UUID userId) {
        return UserReviewResponse.builder().id(id).userId(userId).build();
    }
}
