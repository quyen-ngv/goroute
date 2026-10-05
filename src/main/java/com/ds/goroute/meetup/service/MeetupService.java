package com.ds.goroute.meetup.service;

import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.meetup.dto.MeetupContextResponse;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.dto.MeetupJoinResponse;
import com.ds.goroute.meetup.dto.MeetupParticipantResponse;
import com.ds.goroute.meetup.dto.MeetupSummaryResponse;
import com.ds.goroute.meetup.dto.MeetupUpsertRequest;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupViewerRole;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The app side of meetups (SPEC_V2 §2.4). Every method checks the feature gate first (403
 * MEETUP_DISABLED) and every object read applies the visibility rules: a meetup that is not
 * approved, is taken down, or whose host and viewer have blocked each other reads as 404.
 */
public interface MeetupService {

    MeetupContextResponse context(UUID userId);

    PageResponse<MeetupSummaryResponse> discover(UUID userId, BigDecimal latitude, BigDecimal longitude,
                                                 double radiusKm, List<MeetupCategory> categories, int page, int size);

    /** HOST: every meetup the user hosts, newest first. PARTICIPANT: their live requests and seats. */
    PageResponse<MeetupSummaryResponse> mine(UUID userId, MeetupViewerRole role, int page, int size);

    MeetupDetailResponse detail(UUID userId, UUID meetupId);

    MeetupDetailResponse create(UUID userId, MeetupUpsertRequest request);

    MeetupDetailResponse update(UUID userId, UUID meetupId, MeetupUpsertRequest request);

    MeetupDetailResponse withdraw(UUID userId, UUID meetupId);

    MeetupDetailResponse cancel(UUID userId, UUID meetupId, String reason);

    List<MeetupParticipantResponse> participants(UUID userId, UUID meetupId);

    MeetupJoinResponse join(UUID userId, UUID meetupId, String message);

    MeetupJoinResponse leave(UUID userId, UUID meetupId);

    MeetupParticipantResponse accept(UUID hostUserId, UUID meetupId, UUID participantUserId);

    MeetupParticipantResponse decline(UUID hostUserId, UUID meetupId, UUID participantUserId);

    MeetupParticipantResponse removeParticipant(UUID hostUserId, UUID meetupId, UUID participantUserId);
}
