package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.FeedFeedbackRequest;
import com.ds.goroute.dto.response.FeedFeedbackResponse;
import com.ds.goroute.entity.FeedFeedback;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.FeedFeedbackMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FeedFeedbackService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.FeedFeedbackKind;
import com.ds.goroute.type.FeedItemType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FeedFeedbackServiceImplTest {

    private final FeedFeedbackMapper mapper = mock(FeedFeedbackMapper.class);
    private final UserRepository users = mock(UserRepository.class);
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final FeedFeedbackServiceImpl service = new FeedFeedbackServiceImpl(mapper, users, config);
    private final UUID viewer = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(config.getInt(BusinessConfigKey.FEED_HIDE_AUTHOR_DAYS)).thenReturn(30);
    }

    @Test
    void hidingAPostStoresItForGood() {
        UUID post = UUID.randomUUID();

        FeedFeedbackResponse response = service.hide(viewer, FeedFeedbackRequest.builder()
                .kind(FeedFeedbackKind.HIDE_POST).itemType(FeedItemType.TRIP).itemId(post).build());

        ArgumentCaptor<FeedFeedback> saved = ArgumentCaptor.forClass(FeedFeedback.class);
        verify(mapper).insert(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(viewer);
        assertThat(saved.getValue().getItemId()).isEqualTo(post);
        assertThat(saved.getValue().getExpiresAt()).isNull();
        assertThat(response.getId()).isEqualTo(saved.getValue().getId()).isNotNull();
        assertThat(response.getExpiresAt()).isNull();
    }

    @Test
    void hidingAPersonLastsTheConfiguredDays() {
        UUID author = UUID.randomUUID();
        when(users.findById(author)).thenReturn(Optional.of(new User()));

        FeedFeedbackResponse response = service.hide(viewer, FeedFeedbackRequest.builder()
                .kind(FeedFeedbackKind.HIDE_AUTHOR).targetUserId(author).build());

        assertThat(response.getKind()).isEqualTo(FeedFeedbackKind.HIDE_AUTHOR);
        assertThat(response.getExpiresAt()).isBetween(LocalDateTime.now().plusDays(29), LocalDateTime.now().plusDays(31));
    }

    @Test
    void hidingTheSameThingTwiceReturnsTheFirstHide() {
        FeedFeedback existing = FeedFeedback.builder().id(UUID.randomUUID()).kind(FeedFeedbackKind.HIDE_POST).build();
        when(mapper.findActiveSame(any())).thenReturn(existing);

        FeedFeedbackResponse response = service.hide(viewer, FeedFeedbackRequest.builder()
                .kind(FeedFeedbackKind.HIDE_POST).itemType(FeedItemType.CHECKIN).itemId(UUID.randomUUID()).build());

        assertThat(response.getId()).isEqualTo(existing.getId());
        verify(mapper, never()).insert(any());
    }

    @Test
    void aPostHideNeedsItsTypeAndId() {
        assertThatThrownBy(() -> service.hide(viewer, FeedFeedbackRequest.builder()
                .kind(FeedFeedbackKind.HIDE_POST).itemId(UUID.randomUUID()).build()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void nobodyHidesThemselvesOrSomeoneWhoDoesNotExist() {
        assertThatThrownBy(() -> service.hide(viewer, FeedFeedbackRequest.builder()
                .kind(FeedFeedbackKind.HIDE_AUTHOR).targetUserId(viewer).build()))
                .isInstanceOf(BusinessException.class);
        UUID ghost = UUID.randomUUID();
        when(users.findById(ghost)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.hide(viewer, FeedFeedbackRequest.builder()
                .kind(FeedFeedbackKind.HIDE_AUTHOR).targetUserId(ghost).build()))
                .isInstanceOf(BusinessException.class);
        verify(mapper, never()).insert(any());
    }

    @Test
    void undoRevokesOnlyTheViewersOwnHide() {
        UUID hide = UUID.randomUUID();

        service.unhide(viewer, hide);

        verify(mapper).revoke(hide, viewer);
    }

    @Test
    void hiddenForSplitsPostsAndAuthors() {
        UUID post = UUID.randomUUID();
        UUID author = UUID.randomUUID();
        when(mapper.findActive(viewer)).thenReturn(List.of(
                FeedFeedback.builder().kind(FeedFeedbackKind.HIDE_POST).itemType(FeedItemType.TRIP).itemId(post).build(),
                FeedFeedback.builder().kind(FeedFeedbackKind.HIDE_AUTHOR).targetUserId(author).build()));

        FeedFeedbackService.Hidden hidden = service.hiddenFor(viewer);

        assertThat(hidden.hides(FeedItemType.TRIP, post, UUID.randomUUID())).isTrue();
        assertThat(hidden.hides(FeedItemType.CHECKIN, post, UUID.randomUUID())).as("another kind").isFalse();
        assertThat(hidden.hides(FeedItemType.REVIEW, UUID.randomUUID(), author)).isTrue();
        assertThat(service.hiddenFor(null)).isSameAs(FeedFeedbackService.Hidden.NONE);
    }
}
