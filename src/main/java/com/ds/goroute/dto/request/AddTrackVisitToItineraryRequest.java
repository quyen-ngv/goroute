package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalTime;

/**
 * Turns an off-plan stop into an activity of that day (TR-16a). Every field is optional:
 * the stop's own name and arrival time are used when absent.
 */
@Data
public class AddTrackVisitToItineraryRequest {

    @Size(max = 255)
    @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
    private String name;

    @Size(max = 50)
    private String category;

    /** Trip-local "HH:mm". */
    private LocalTime startTime;
}
