package com.ds.goroute.dto.response;

import com.ds.goroute.type.SavedCategoryVisibility;
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
public class SavedCategoryResponse {
    private UUID id;
    private UUID ownerId;
    private String name;
    /** The photo the owner chose; null when none was. */
    private String coverUrl;
    /** The newest item's photo, shown when there is no {@link #coverUrl}. */
    private String previewPhotoUrl;
    private SavedCategoryVisibility visibility;
    private int itemCount;
    private LocalDateTime lastSavedAt;
    private LocalDateTime updatedAt;
}
