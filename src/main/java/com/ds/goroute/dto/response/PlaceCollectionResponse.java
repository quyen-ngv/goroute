package com.ds.goroute.dto.response;

import com.ds.goroute.type.ContentVisibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class PlaceCollectionResponse {
    private UUID id;
    private UUID ownerId;
    private String ownerDisplayName;
    private String name;
    private String description;
    private String coverImageUrl;
    private ContentVisibility visibility;
    /** Present only while the collection is public. */
    private String shareSlug;
    private int itemCount;
    private int viewCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<PlaceCollectionItemResponse> items;
}
