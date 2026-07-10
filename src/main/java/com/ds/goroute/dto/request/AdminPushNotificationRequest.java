package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPushNotificationRequest {

    /**
     * List of recipient emails (1 or more)
     */
    @NotEmpty(message = "At least one email is required")
    private List<String> emails;

    /**
     * Notification title
     */
    @NotBlank(message = "Title is required")
    @Size(max = 100, message = "Title must not exceed 100 characters")
    private String title;

    /**
     * Notification body/brief
     */
    @NotBlank(message = "Body is required")
    @Size(max = 300, message = "Body must not exceed 300 characters")
    private String body;

    /**
     * Deep link for redirect (e.g., "/trips/{tripId}", "/notifications")
     * Optional - if null, opens app home
     */
    private String deepLink;

    /**
     * Optional custom data (e.g., {"tripId": "uuid", "activityId": "uuid"})
     */
    private Map<String, Object> data;

    /**
     * Optional image URL for rich notification
     */
    private String imageUrl;

    /**
     * Priority: "high" or "normal" (default: high)
     */
    private String priority;
}
