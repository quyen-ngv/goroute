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
public class LocationImageResponse {
    private UUID id;
    private String fullAddress;
    private String imageUrl;
    private Integer priority;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
