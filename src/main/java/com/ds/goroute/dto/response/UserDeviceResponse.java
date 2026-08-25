package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class UserDeviceResponse {
    UUID id;
    String deviceType;
    String deviceName;
    String language;
    Boolean isActive;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
