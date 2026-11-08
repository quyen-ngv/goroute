package com.ds.goroute.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A person in a settlement: {@code userId} or, inside a trip, {@code guestMemberId}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LedgerParticipantRequest {
    private UUID userId;
    private UUID guestMemberId;
}
