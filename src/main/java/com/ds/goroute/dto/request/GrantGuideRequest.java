package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
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
}
