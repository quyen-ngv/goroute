package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One {@code admin_api_keys} row. Only the SHA-256 of the key is ever stored. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApiKey {

    private UUID id;
    private String name;
    private String keyPrefix;
    private String keyHash;
    private UUID ownerUserId;
    /** JSON array of scope codes, e.g. ["events:import"]. */
    private String scopes;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;

    /** Read-only: users.full_name of the owner. */
    private String ownerName;
}
