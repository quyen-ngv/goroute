package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/** User-aware Passport tags that can be earned from one catalogue Place. */
@Data
@Builder
public class PlacePassportTagsResponse {
    private List<PassportTagResponse> tags;
    private int checkinCount;
}
