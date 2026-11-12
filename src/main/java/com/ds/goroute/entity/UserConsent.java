package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One answer in a person's consent history (V278 {@code user_consents}). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserConsent {
    private UUID id;
    private UUID userId;
    private String purpose;
    private Boolean granted;
    private String documentVersion;
    private String source;
    private String platform;
    private String appVersion;
    private LocalDateTime createdAt;
}
