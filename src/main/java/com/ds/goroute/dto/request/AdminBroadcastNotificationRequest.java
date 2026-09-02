package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** One admin announcement: what to say, and who to say it to. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminBroadcastNotificationRequest {

    @NotNull(message = "Audience is required")
    @Valid
    private AdminNotificationAudienceRequest audience;

    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;

    @NotBlank(message = "Body is required")
    @Size(max = 300, message = "Body must not exceed 300 characters")
    private String body;

    /** In-app route opened when the notification is tapped, for example {@code /trips/{id}}. */
    @Size(max = 500, message = "Deep link must not exceed 500 characters")
    private String deepLink;

    @Size(max = 500, message = "Image URL must not exceed 500 characters")
    private String imageUrl;

    /** Extra payload merged into the push data map. */
    private Map<String, Object> data;

    /** "high" or "normal"; defaults to high. */
    private String priority;
}
