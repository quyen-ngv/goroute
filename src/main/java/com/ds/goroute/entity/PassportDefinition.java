package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** A curated travel passport that groups independently managed proof tags. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PassportDefinition {
    private UUID id;
    private String code;
    private String name;
    private String description;
    /** English copy; blank falls back to the Vietnamese name/description. */
    private String nameEn;
    private String descriptionEn;
    private String coverImageUrl;
    private Boolean isActive;
    private Integer displayOrder;
    /** Curated Location Image anchors used to derive the default Place scope. */
    private List<UUID> locationImageIds;
    /** Legacy province scope retained for rolling compatibility only. */
    private List<String> provinceCodes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
