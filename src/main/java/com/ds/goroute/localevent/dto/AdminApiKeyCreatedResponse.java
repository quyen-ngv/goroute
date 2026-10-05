package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Answer of POST /v1/api/admin/api-keys. {@code key} is returned here and nowhere else: only its
 * SHA-256 is stored, so a lost key cannot be shown again (revoke it and create another).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApiKeyCreatedResponse {

    private UUID id;
    private String name;
    private String keyPrefix;
    private String key;
    private List<String> scopes;
    private UUID ownerUserId;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
}
