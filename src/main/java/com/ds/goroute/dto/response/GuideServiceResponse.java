package com.ds.goroute.dto.response;

import com.ds.goroute.type.GuidePricingMode;
import com.ds.goroute.type.GuideServiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GuideServiceResponse {
    private UUID id;
    private UUID guideId;
    private String guideDisplayName;
    private String guideAvatarUrl;
    private String title;
    private String summary;
    private String itinerary;
    private BigDecimal durationHours;
    private Integer maxGuests;
    private String meetingPoint;
    private String provinceCode;
    private List<String> inclusions;
    private List<String> exclusions;
    private GuidePricingMode pricingMode;
    private BigDecimal priceAmount;
    private String currency;
    /** Spelled out so the price is never ambiguous at the meeting point. */
    private String priceLabel;
    private Integer advanceNoticeHours;
    private GuideServiceStatus status;
    /**
     * Paid placement. Part of the payload, not a decision the screen makes, so a promoted
     * result cannot be rendered anywhere without its label.
     */
    private boolean promoted;
}
