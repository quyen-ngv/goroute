package com.ds.goroute.entity;

import com.ds.goroute.type.GuidePricingMode;
import com.ds.goroute.type.GuideServiceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One thing a guide offers.
 *
 * <p>{@code inclusions} and {@code exclusions} are structured lists rather than prose.
 * Whether the entrance ticket, lunch and transport are covered is where most disputes
 * start, and a structured answer can also be compared between two guides.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideService {
    private UUID id;
    private UUID guideId;
    private String title;
    private String summary;
    private String itinerary;
    private BigDecimal durationHours;
    private Integer maxGuests;
    private String meetingPoint;
    private String provinceCode;
    private String inclusions;
    private String exclusions;
    private GuidePricingMode pricingMode;
    private BigDecimal priceAmount;
    private String currency;
    private Integer advanceNoticeHours;
    private GuideServiceStatus status;
    /**
     * Whether this listing is a paid placement. Carried in the data rather than decided by
     * the screen, so a promoted result cannot appear anywhere without its label.
     */
    private Boolean isPromoted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
