package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPushNotificationResponse {

    /**
     * Total emails requested
     */
    private Integer totalRequested;

    /**
     * Number of users found and notification sent
     */
    private Integer successCount;

    /**
     * Number of users not found
     */
    private Integer notFoundCount;

    /**
     * List of emails that were not found
     */
    private List<String> notFoundEmails;

    /**
     * Number of users found but have no active devices
     */
    private Integer noDeviceCount;

    /**
     * List of emails with no active devices
     */
    private List<String> noDeviceEmails;

    /**
     * Number of users found with devices but push delivery failed
     */
    private Integer failedCount;

    /**
     * List of emails whose push delivery failed
     */
    private List<String> failedEmails;

    /**
     * Details message
     */
    private String message;
}
