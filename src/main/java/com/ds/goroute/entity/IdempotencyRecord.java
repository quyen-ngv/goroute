package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One claimed Idempotency-Key: table {@code idempotency_keys} (V213). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyRecord {
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";

    private UUID id;
    private UUID userId;
    private String idempotencyKey;
    private String requestMethod;
    private String requestPath;
    private String requestFingerprint;
    private String state;
    private Integer responseStatus;
    private String responseContentType;
    private byte[] responseBody;
    private LocalDateTime lockedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime expiresAt;
}
