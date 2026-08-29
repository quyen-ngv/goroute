package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class PlaceCollectionItemResponse {
    private UUID id;
    private UUID placeId;
    private String displayName;
    private String displayNote;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private int position;
    private String thumbnail;
    private String category;
    private BigDecimal rating;
    /**
     * False when the catalogue row behind this entry is gone or no longer public. The
     * entry still renders, marked -- vanishing quietly looks like the collection broke.
     */
    private boolean placeAvailable;
}
