package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupParticipantStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** The caller's participant status after join or leave. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupJoinResponse {

    private MeetupParticipantStatus status;
}
