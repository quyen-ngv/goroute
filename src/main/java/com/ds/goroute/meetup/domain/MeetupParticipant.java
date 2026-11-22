package com.ds.goroute.meetup.domain;

import com.ds.goroute.type.MeetupParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of {@code meetup_participants}: a person other than the host and their status. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupParticipant {

    private UUID meetupId;
    private UUID userId;
    private MeetupParticipantStatus status;
    private String message;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
    private LocalDateTime updatedAt;
}
