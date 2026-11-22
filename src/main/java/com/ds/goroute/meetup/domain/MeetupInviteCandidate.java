package com.ds.goroute.meetup.domain;

import com.ds.goroute.type.MeetupParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Somebody a host may invite, with their participant row in the meetup if they have one. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupInviteCandidate {

    private UUID userId;
    private String fullName;
    private String username;
    private String avatarUrl;
    private MeetupParticipantStatus status;
}
