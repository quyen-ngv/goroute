package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletPersonReminderResponse {
    private UUID recipientUserId;
    /** The splits the notification named; settled ones and trips the recipient left are dropped. */
    private List<UUID> splitIds;
    private LocalDateTime sentAt;
}
