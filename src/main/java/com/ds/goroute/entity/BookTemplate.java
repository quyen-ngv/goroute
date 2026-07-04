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
public class BookTemplate {
    private UUID id;
    private String templateType;
    private String refId;
    private String refName;
    private String backgrounds;
    private String stickers;
    private String frameStyles;
    private String colorPalette;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
