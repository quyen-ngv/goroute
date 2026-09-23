package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * An award rule as data (PAS-04).
 *
 * <p>Rules are rows rather than code branches so that adding one is an operator action.
 * They are versioned so that changing a rule never rewrites what somebody already earned:
 * a stamp keeps the version it was granted under, and can still be explained years later.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassportStampRule {
    private String code;
    private Integer version;
    private String name;
    private String description;
    /** English copy; blank falls back to the Vietnamese name/description. */
    private String nameEn;
    private String descriptionEn;
    private String conditionType;
    private Integer threshold;
    private String icon;
    private Integer rewardPoints;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
