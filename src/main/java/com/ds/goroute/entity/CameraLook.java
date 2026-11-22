package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of {@code camera_looks}. Enum-valued columns are kept as their database strings. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CameraLook {
    private UUID id;
    private String kind;
    private String lookKey;
    /** JSON object: language tag to name. */
    private String names;
    /** JSON object; its shape depends on the kind. */
    private String definition;
    private String thumbnailUrl;
    private Integer sortOrder;
    private String status;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private String source;
    private String licenseNote;
    private Integer version;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
