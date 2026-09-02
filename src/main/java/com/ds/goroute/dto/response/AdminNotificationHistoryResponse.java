package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One past announcement, reconstructed from the notification rows it produced. Sends are not
 * stored as a campaign record, so a batch is identified by its title, body and send minute.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationHistoryResponse {

    private String title;
    private String body;
    private String deepLink;
    private LocalDateTime sentAt;
    private long recipientCount;
    private long readCount;
}
