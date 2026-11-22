package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of {@code asset_library}. Enum-valued columns are kept as their database strings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryAsset {
    private UUID id;
    private String collection;
    private String assetKey;
    private String nameVi;
    private String nameEn;
    private String nameJa;
    private String nameKo;
    private String description;
    /** JSON array of strings. */
    private String tags;
    private String imageUrl;
    private String source;
    private String sourceUrl;
    private String licenseNote;
    private String status;
    private Integer version;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
