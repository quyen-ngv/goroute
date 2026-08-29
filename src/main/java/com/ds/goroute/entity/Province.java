package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One province of the official list (PAS-01).
 *
 * <p>{@code aliases} is the working part: a free-text address says "TP Ha Noi" or "Hanoi"
 * or "Thanh pho Ha Noi", and all of them have to resolve to one row before anything can be
 * counted.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Province {
    private String code;
    private String name;
    private String normalizedName;
    private String region;
    private String aliases;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** Which official list this row belongs to; two versions must not be mixed. */
    private String datasetVersion;
    private Boolean isActive;
    private LocalDateTime createdAt;
}
