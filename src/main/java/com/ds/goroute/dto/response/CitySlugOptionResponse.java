package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CitySlugOptionResponse {
    private String slug;
    private String label;
    /** Region grouping key: "north" | "central" | "south". Nullable. */
    private String regionKey;
    /** Short teaser for the regional card. Nullable. */
    private String shortDescription;
    /** Optional dish photo specific to this city. Nullable. */
    private String imageUrl;
}
