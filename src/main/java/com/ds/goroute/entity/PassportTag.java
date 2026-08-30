package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** A piece of verifiable travel evidence belonging to a passport definition. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassportTag {
    private UUID id;
    private UUID passportId;
    /** Joined only on read models; never persisted in passport_tags itself. */
    private String passportCode;
    private String passportName;
    private String code;
    private String name;
    private String description;
    private String imageUrl;
    /** SPECIFIC_PLACES, PASSPORT_LOCATIONS, or legacy PASSPORT_PROVINCES. */
    private String qualificationMode;
    private Integer requiredCheckinCount;
    private Boolean isActive;
    private Integer displayOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /** Set for a traveller's earned-tag projection. */
    private LocalDateTime earnedAt;
    private UUID triggeringEventId;
}
