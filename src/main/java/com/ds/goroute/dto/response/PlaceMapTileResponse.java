package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** One map tile's places, most prominent first; an empty list is a tile with none. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaceMapTileResponse {
    private int z;
    private int x;
    private int y;
    private List<PlaceMapPinResponse> places;
}
