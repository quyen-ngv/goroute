package com.ds.goroute.entity;

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
public class BookPageSlot {
    private UUID id;
    private UUID pageId;
    private String slotId;
    private String type;
    private String value;
    private Boolean visible;
    private String frameStyle;
    private String cropRect;
    private String transform;
    private String style;
    private Boolean locked;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
