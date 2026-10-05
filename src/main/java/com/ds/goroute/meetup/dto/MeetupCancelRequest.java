package com.ds.goroute.meetup.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code POST /v1/api/meetups/{id}/cancel}. The reason is shown to the people who joined. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MeetupCancelRequest {

    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.MEETUP, visibility = ModerationVisibility.GROUP)
    private String reason;
}
