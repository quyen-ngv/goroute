package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedCategoryDetailResponse {
    private SavedCategoryResponse category;
    /** Newest first. Tags are left out unless the viewer owns the category. */
    private List<SavedPlaceResponse> items;
    /** Whether the viewer may edit the category and its items. JSON {@code owner}. */
    private boolean owner;
}
