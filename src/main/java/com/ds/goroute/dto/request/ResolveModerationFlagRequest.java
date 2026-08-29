package com.ds.goroute.dto.request;

import com.ds.goroute.type.ModerationFlagStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResolveModerationFlagRequest {

    /** KEPT, REMOVED or ESCALATED. KEPT is recorded as a false-positive signal. */
    @NotNull(message = "A decision is required")
    private ModerationFlagStatus status;

    @Size(max = 1000)
    private String note;
}
