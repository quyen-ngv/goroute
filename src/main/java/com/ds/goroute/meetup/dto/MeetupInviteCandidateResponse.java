package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A person the host may invite: someone they follow or who follows them. {@code status} is
 * their participant row in this meetup, {@code null} when they have none; the app offers
 * "Invite" unless it is REQUESTED, INVITED or JOINED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupInviteCandidateResponse {

    private MeetupPersonResponse user;
    private MeetupParticipantStatus status;
}
