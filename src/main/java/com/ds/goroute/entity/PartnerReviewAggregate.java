package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Review count and average rating over an organization's hotels and activities for a quality window. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerReviewAggregate {
    private Integer reviewCount;
    private BigDecimal reviewAverage;
}
