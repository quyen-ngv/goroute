package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A safe display identity for a registered trip member or an unlinked guest. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletPersonResponse {
    private UUID userId;
    private UUID guestMemberId;
    private String displayName;
    private String avatarUrl;
    private Boolean isGuest;
}
