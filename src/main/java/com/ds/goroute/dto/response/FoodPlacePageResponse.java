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
public class FoodPlacePageResponse {
    private List<FoodPlaceItemResponse> items;
    private long total;
    private boolean locationEnabled;
    private int page;
    private int size;
}
