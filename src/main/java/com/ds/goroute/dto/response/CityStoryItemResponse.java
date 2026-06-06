package com.ds.goroute.dto.response;

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
public class CityStoryItemResponse {
    private UUID id;
    private UUID locationImageId;
    private String imageUrl;
    private String description;
    private UUID placeId;
    private CityStoryPlaceSummary place;
    private int likeCount;
    private boolean hasLiked;
    private boolean hasViewed;
    private LocalDateTime createdAt;
}
