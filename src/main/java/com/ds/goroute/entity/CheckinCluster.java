package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A group of check-ins that share a location key, as seen by the promotion queue (CHK-12).
 *
 * <p>Not a stored row: it is the result of grouping, and it only becomes real data if an
 * operator promotes it. Ranked by <em>distinct users</em> before check-in count, because
 * twenty visits by one person is a habit and twenty visits by fifteen people is a place.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinCluster {
    private String locationKey;
    private String commonName;
    /** Other names people used for the same spot, for the operator to judge by. */
    private String alternateNames;
    private BigDecimal centroidLatitude;
    private BigDecimal centroidLongitude;
    private String ward;
    private String district;
    private String province;
    private String provinceCode;
    private Integer checkinCount;
    private Integer distinctUserCount;
    private Integer ratedCheckinCount;
    private LocalDateTime firstSeenAt;
    private LocalDateTime lastSeenAt;
    /** A few photos: looking at them is the fastest way to tell a real place from noise. */
    private String samplePhotos;
}
