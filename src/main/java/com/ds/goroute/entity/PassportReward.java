package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Operator-managed reward catalogue entry backed by the Passport points wallet. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassportReward {
    private UUID id;
    private String code;
    private String name;
    private String description;
    /** English copy; blank falls back to the Vietnamese name/description. */
    private String nameEn;
    private String descriptionEn;
    private Integer pointsCost;
    private String requiredStampCode;
    private Integer totalQuantity;
    private Integer issuedQuantity;
    private Integer validDays;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
