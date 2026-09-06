package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletReminderResponse {
    private UUID id;
    private UUID expenseId;
    private UUID splitId;
    private UUID recipientUserId;
    private LocalDateTime sentAt;
}
