package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** One participant row. {@code message} is filled for the host only. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupParticipantResponse {

    private MeetupPersonResponse user;
    private MeetupParticipantStatus status;
    private String message;
    private LocalDateTime requestedAt;
    private LocalDateTime decidedAt;
}
