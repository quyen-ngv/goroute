package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** An admin API key as listed; never carries the key itself. {@code status}: ACTIVE, REVOKED or EXPIRED. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApiKeyResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    private List<String> scopes;
    private UUID ownerUserId;
    private String ownerName;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime lastUsedAt;
    private LocalDateTime revokedAt;
    private String status;
}
