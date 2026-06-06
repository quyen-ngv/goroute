package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CityStoryGroupResponse {
    private UUID locationId;
    private String fullAddress;
    private String citySlug;
    private String avatarUrl;
    private boolean hasUnviewedStories;
    private List<CityStoryItemResponse> stories;
}
