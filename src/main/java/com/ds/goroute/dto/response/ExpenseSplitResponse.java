package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseSplitResponse {
    private UUID id;
    private UserResponse user;
    /** trip_members.id of a guest split; null for a registered user's split. */
    private UUID guestMemberId;
    private BigDecimal amount;
    private Boolean isPaid;
}
