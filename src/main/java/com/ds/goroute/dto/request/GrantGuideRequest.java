package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrantGuideRequest {

    /** What the badge says, when the guide has a speciality worth naming. */
    @Size(max = 120)
    private String displayTitle;

    /** Why they were vouched for. Operator-facing, never shown in the app. */
    @Size(max = 2000)
    private String note;

    /**
     * Tourist areas this guide covers. A guide routinely works several, and unlike a place
     * or a hotel there is no coordinate to derive them from - an operator states them.
     *
     * <p>Null leaves the current areas untouched; an empty list clears them.
     */
    private List<UUID> locationImageIds;
}
