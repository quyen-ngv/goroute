package com.ds.goroute.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class AiTripCandidateQueryRequest {
    @NotNull private BigDecimal latitude;
    @NotNull private BigDecimal longitude;
    private List<String> placeGroups;
    /** Tourist area the destination was picked from; when absent the area is derived from the coordinates. */
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(using = com.ds.goroute.utils.json.LenientUuidDeserializer.class)
    private java.util.UUID locationImageId;
    @Min(1) @Max(500) private int limit = 250;
}
