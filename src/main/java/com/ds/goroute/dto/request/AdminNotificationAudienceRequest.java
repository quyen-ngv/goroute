package com.ds.goroute.dto.request;

import com.ds.goroute.type.AdminNotificationAudience;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** The segment an admin announcement is addressed to. Shared by preview and send. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationAudienceRequest {

    @NotNull(message = "Audience is required")
    private AdminNotificationAudience audience;

    /** Required for {@code SPECIFIC_USERS}, ignored otherwise. */
    private List<UUID> userIds;

    /** Day window for the time-based segments. Defaults to 7 when the segment needs one. */
    @Min(value = 1, message = "withinDays must be at least 1")
    @Max(value = 365, message = "withinDays must not exceed 365")
    private Integer withinDays;

    /**
     * Skip accounts with no registered device. Defaults to true for a send — a notification row
     * nobody can receive is noise — and to false for a preview, so the operator sees the gap.
     */
    private Boolean onlyWithDevice;
}
