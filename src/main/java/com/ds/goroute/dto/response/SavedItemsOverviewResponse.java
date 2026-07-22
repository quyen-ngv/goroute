package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * The complete saved-state snapshot used to render favourite controls.
 * Saved items retain their optional collection/category and itinerary items
 * identify every trip in which the current user has included that item.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedItemsOverviewResponse {
    private List<SavedPlaceResponse> savedItems;
    private List<SavedItemTripResponse> tripItems;
}
