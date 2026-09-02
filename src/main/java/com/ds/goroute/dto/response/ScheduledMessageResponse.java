package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/** A scheduled message rule plus the two figures the console shows to prove it is working. */
@Data
@Builder
public class ScheduledMessageResponse {
    private UUID id;
    private UUID organizationId;
    private UUID templateId;
    private String trigger;
    private Integer offsetHours;
    private String body;
    private String appliesTo;
    private String status;
    private LocalDateTime lastRunAt;
    private long sentCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
