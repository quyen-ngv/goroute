package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AppConfigResponse {
    private UUID id;
    private String label;
    private String key;
    private String value;
    private String description;
    private Boolean isActive;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
