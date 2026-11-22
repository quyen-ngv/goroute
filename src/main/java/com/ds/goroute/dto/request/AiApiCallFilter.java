package com.ds.goroute.dto.request;

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
public class AiApiCallFilter {
    private String feature;
    private String operation;
    private String model;
    private String status;
    private String traceId;
    private UUID userId;
    /** Inclusive. */
    private LocalDateTime from;
    /** Exclusive. */
    private LocalDateTime to;
}
