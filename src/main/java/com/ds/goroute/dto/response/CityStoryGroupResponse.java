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
    /**
     * Live weather for the city, so the stories strip can label a card without a
     * second round-trip and the story viewer opens on data it already holds.
     *
     * <p>{@code null} when the location has no coordinates or the provider is down.
     */
    private CityWeatherResponse weather;
}
