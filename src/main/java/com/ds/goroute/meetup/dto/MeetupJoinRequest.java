package com.ds.goroute.meetup.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code POST /v1/api/meetups/{id}/join}. The message only matters for APPROVAL meetups. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupJoinRequest {

    /** Optional note to the host; only the host reads it. */
    @Size(max = 140)
    @ModeratedText(contentType = ModeratedContentType.MEETUP, visibility = ModerationVisibility.DIRECT)
    private String message;
}
