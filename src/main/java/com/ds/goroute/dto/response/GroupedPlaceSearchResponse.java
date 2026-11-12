package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * GET /places/search/grouped: the page GET /places/search returns for the same query
 * ({@link #nearby}), and on the first page the places elsewhere that match the keyword by name
 * when the area itself finds none, or only names sharing one word ({@link #elsewhere}).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupedPlaceSearchResponse {

    /** Exactly what GET /places/search returns for the same parameters. */
    private List<PlaceResponse> nearby;
    /**
     * Beyond the radius, best name match first, one per name and province, each with
     * {@code provinceName} and {@code distance}. Empty on later pages and when switched off.
     */
    private List<PlaceResponse> elsewhere;
    /** More far places matched than {@link #elsewhere} holds. */
    private boolean elsewhereTruncated;
}
