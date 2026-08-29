package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.GuidePricingMode;
import com.ds.goroute.type.GuideServiceStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class UpsertGuideServiceRequest {

    @NotBlank(message = "A title is required")
    @Size(max = 300)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_SERVICE, visibility = ModerationVisibility.PUBLIC)
    private String title;

    @Size(max = 3000)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_SERVICE, visibility = ModerationVisibility.PUBLIC)
    private String summary;

    @Size(max = 10000)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_SERVICE, visibility = ModerationVisibility.PUBLIC)
    private String itinerary;

    @NotNull @DecimalMin("0.5")
    private BigDecimal durationHours;

    @NotNull @Min(1)
    private Integer maxGuests;

    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.GUIDE_SERVICE, visibility = ModerationVisibility.PUBLIC)
    private String meetingPoint;

    @Size(max = 10)
    private String provinceCode;

    /**
     * Structured rather than prose: what is and is not covered is where disputes start,
     * and a list can be compared between two services.
     */
    private List<String> inclusions;
    private List<String> exclusions;

    @NotNull(message = "State whether the price is per person or per group")
    private GuidePricingMode pricingMode;

    @NotNull @DecimalMin("0.0")
    private BigDecimal priceAmount;

    @Size(max = 3)
    private String currency;

    @Min(0)
    private Integer advanceNoticeHours;

    private GuideServiceStatus status;
}
