package com.ds.goroute.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.HotelPaymentMethod;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Shape of {@code hotel_profiles.policies}. Unknown keys written by older consoles are ignored
 * on read, so the column never needs a data migration when a key is added here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HotelPolicies {
    private static final String TIME = "^([01]\\d|2[0-3]):[0-5]\\d$";

    /** Minimum age of the guest who checks in. */
    @Min(0) @Max(99)
    private Integer minCheckInAge;

    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    @Size(max = 3000)
    private String checkInInstructions;

    private Boolean childrenAllowed;

    @Min(0) @Max(17)
    private Integer minimumChildAge;

    /** Children up to this age stay free when they use existing beds. */
    @Min(0) @Max(17)
    private Integer freeStayChildMaxAge;

    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    @Size(max = 2000)
    private String childPolicyNote;

    private Boolean petsAllowed;

    @ModeratedText(contentType = ModeratedContentType.PARTNER_LISTING, visibility = ModerationVisibility.PUBLIC)
    @Size(max = 1000)
    private String petPolicyNote;

    private Boolean smokingAllowed;

    @Pattern(regexp = TIME)
    private String quietHoursStart;

    @Pattern(regexp = TIME)
    private String quietHoursEnd;

    @Size(max = 10)
    private List<HotelPaymentMethod> paymentMethods;

    /** Charged at the property on top of the booking price, e.g. city tax. */
    @Valid @Size(max = 20)
    private List<HotelFee> mandatoryFees;

    /** Charged only when the guest asks, e.g. breakfast, crib, extra bed. */
    @Valid @Size(max = 20)
    private List<HotelFee> optionalFees;
}
