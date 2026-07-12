package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripSearchBiasResponse {
    private BigDecimal lat;
    private BigDecimal lng;
    private String source;
    private String label;
    private UUID destinationId;
    private String destinationName;
}
