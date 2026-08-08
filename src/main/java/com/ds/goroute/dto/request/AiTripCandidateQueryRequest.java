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
    @Min(1) @Max(500) private int limit = 250;
}
