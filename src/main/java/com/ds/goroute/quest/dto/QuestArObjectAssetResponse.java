package com.ds.goroute.quest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One 3D object in the AR library (§3.15), as the console and the builder list it. */
public record QuestArObjectAssetResponse(
        UUID id,
        String name,
        String description,
        String glbUrl,
        Long glbBytes,
        String usdzUrl,
        Long usdzBytes,
        String thumbnailUrl,
        BigDecimal heightM,
        List<QuestArObjectView.Clip> clips,
        Integer triangles,
        boolean canWander,
        List<String> tags,
        boolean active,
        long dataVersion,
        LocalDateTime updatedAt) {
}
