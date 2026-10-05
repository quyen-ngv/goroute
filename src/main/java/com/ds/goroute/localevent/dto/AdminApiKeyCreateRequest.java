package com.ds.goroute.localevent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Body of POST /v1/api/admin/api-keys. The owner defaults to the caller and must be an admin. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminApiKeyCreateRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    private UUID ownerUserId;

    /** ISO-8601 with offset; must be in the future. Null = never expires. */
    private OffsetDateTime expiresAt;
}
