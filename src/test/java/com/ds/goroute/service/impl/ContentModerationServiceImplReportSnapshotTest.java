package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.ReportContentRequest;
import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.entity.ModerationFlag;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.ActivityCommentRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.type.ContentReportReason;
import com.ds.goroute.type.ModeratedContentType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A user report stores what was reported in {@code moderation_flags.context_snapshot}, a JSONB
 * column the console parses as an object. A bare string (the meetup title, a comment text) made
 * PostgreSQL reject the insert, so every such report answered 500.
 */
@ExtendWith(MockitoExtension.class)
class ContentModerationServiceImplReportSnapshotTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Mock
    private ModerationFlagRepository flagRepository;
    @Mock
    private ContentCommentRepository contentCommentRepository;
    @Mock
    private ActivityCommentRepository activityCommentRepository;
    @Mock
    private MeetupRepository meetupRepository;

    @InjectMocks
    private ContentModerationServiceImpl service;

    @Test
    void meetupReportStoresTheTitleAsAJsonObjectAndTheHostAsOwner() throws Exception {
        UUID meetupId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        when(meetupRepository.findById(meetupId)).thenReturn(Optional.of(
                Meetup.builder().id(meetupId).hostUserId(hostId).title("Cà phê sáng \"cuối tuần\"").build()));
        when(flagRepository.findOpen(anyString(), any(), anyString(), anyString())).thenReturn(Optional.empty());

        service.report(UUID.randomUUID(), report(ModeratedContentType.MEETUP, meetupId));

        ModerationFlag flag = capturedFlag();
        assertThat(flag.getContentOwnerId()).isEqualTo(hostId);
        JsonNode snapshot = JSON.readTree(flag.getContextSnapshot());
        assertThat(snapshot.isObject()).isTrue();
        assertThat(snapshot.get("field").asText()).isEqualTo("title");
        assertThat(snapshot.get("text").asText()).isEqualTo("Cà phê sáng \"cuối tuần\"");
    }

    @Test
    void commentReportStoresTheCommentAsAJsonObject() throws Exception {
        UUID commentId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        when(contentCommentRepository.findById(commentId)).thenReturn(Optional.of(
                ContentComment.builder().id(commentId).userId(authorId).content("hello").isDeleted(false).build()));
        when(flagRepository.findOpen(anyString(), any(), anyString(), anyString())).thenReturn(Optional.empty());

        service.report(UUID.randomUUID(), report(ModeratedContentType.CONTENT_COMMENT, commentId));

        JsonNode snapshot = JSON.readTree(capturedFlag().getContextSnapshot());
        assertThat(snapshot.get("field").asText()).isEqualTo("content");
        assertThat(snapshot.get("text").asText()).isEqualTo("hello");
    }

    @Test
    void reportSnapshotIsNullWhenThereIsNoText() {
        assertThat(ContentModerationServiceImpl.reportSnapshot("title", null)).isNull();
    }

    private ModerationFlag capturedFlag() {
        ArgumentCaptor<ModerationFlag> captor = ArgumentCaptor.forClass(ModerationFlag.class);
        verify(flagRepository).upsert(captor.capture());
        return captor.getValue();
    }

    private static ReportContentRequest report(ModeratedContentType type, UUID contentId) {
        return ReportContentRequest.builder()
                .contentType(type)
                .contentId(contentId)
                .reason(ContentReportReason.SPAM_OR_ADVERTISING)
                .build();
    }
}
