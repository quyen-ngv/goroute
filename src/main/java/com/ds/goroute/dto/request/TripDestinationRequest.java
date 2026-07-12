package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripDestinationRequest {
    @Size(max = 255)
    private String name;
    private String address;
    private String placeId;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer orderIndex;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isPrimary;
}
