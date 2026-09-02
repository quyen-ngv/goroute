package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One account in an audience preview or in the operator's user picker. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminNotificationRecipientResponse {

    private UUID id;
    private String username;
    private String fullName;
    private String email;
    private String avatarUrl;
    private String accountStatus;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
    private Integer deviceCount;
    private Integer tripCount;
    /** Start date of the trip that put this account in the segment, when the segment is trip based. */
    private LocalDate nextTripStartDate;
}
