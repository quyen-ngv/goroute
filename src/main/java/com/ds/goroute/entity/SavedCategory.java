package com.ds.goroute.entity;

import com.ds.goroute.type.SavedCategoryVisibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A category of the user's saved items, as a record of its own.
 *
 * <p>Saved items name their category by text; an item belongs here when its trimmed category
 * matches {@link #name} ignoring case. The read-side counts ({@link #itemCount},
 * {@link #latestPhotoUrl}, {@link #lastSavedAt}) are filled only by the list queries.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedCategory {
    private UUID id;
    private UUID userId;
    private String name;
    /** A photo the owner chose; null means the newest item's photo stands in. */
    private String coverUrl;
    private SavedCategoryVisibility visibility;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private Integer itemCount;
    private String latestPhotoUrl;
    private LocalDateTime lastSavedAt;
}
