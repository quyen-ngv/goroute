package com.ds.goroute.thirdparty.google.places;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/** Body of a {@code places:searchText} answer. Google omits {@code places} when nothing matched. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GooglePlacesTextSearchResponse {
    private List<GooglePlace> places;
}
