package com.ds.goroute.partneronboarding.submit;

import com.ds.goroute.dto.HotelPolicies;
import com.ds.goroute.dto.request.BulkUpdateRatePlanCalendarRequest;
import com.ds.goroute.dto.request.UpsertHotelRequest;
import com.ds.goroute.dto.request.UpsertPartnerPlaceRequest;
import com.ds.goroute.dto.request.UpsertRatePlanPromotionRequest;
import com.ds.goroute.dto.request.UpsertRatePlanRequest;
import com.ds.goroute.dto.request.UpsertRoomTypeRequest;
import com.ds.goroute.dto.response.HotelProfileResponse;
import com.ds.goroute.dto.response.PartnerPlaceResponse;
import com.ds.goroute.dto.response.RatePlanResponse;
import com.ds.goroute.dto.response.RoomTypeResponse;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import com.ds.goroute.partneronboarding.domain.OnboardingSteps;
import com.ds.goroute.partneronboarding.domain.StepData;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.PartnerPlaceService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.HotelPropertyType;
import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import com.ds.goroute.type.MarketplacePublicationStatus;
import com.ds.goroute.type.MealPlan;
import com.ds.goroute.type.RoomBathroomType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a place, a property, one room type, one rate plan and its opening offers.
 *
 * <p>The wizard asks a host the Airbnb questions — how many guests, how many bathrooms, what
 * it costs a night — and those answers do not line up one-to-one with the marketplace model,
 * which is built for a property with many room types and rate plans. So this class makes the
 * simplifying choice the wizard implies: one room type with one unit, one standard rate. A
 * host with a second room type adds it in the workspace afterwards, where the full model is
 * available; nobody is asked to understand rate plans on their first day.
 */
@Component
@RequiredArgsConstructor
public class StayMaterializer implements ListingMaterializer {

    /** The single room type and rate the wizard creates. Short, because partners see them. */
    private static final String DEFAULT_CODE = "STD";
    private static final LocalTime DEFAULT_CHECK_IN = LocalTime.of(14, 0);
    private static final LocalTime DEFAULT_CHECK_OUT = LocalTime.of(12, 0);
    /** Friday and Saturday nights, which is what "weekend price" means to a host. */
    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    private static final int EARLY_BIRD_ADVANCE_DAYS = 14;
    private static final int LIMITED_TIME_BOOKING_WINDOW_DAYS = 90;
    private static final int WEEKLY_MIN_NIGHTS = 7;
    private static final int MONTHLY_MIN_NIGHTS = 28;

    private final PartnerPlaceService partnerPlaceService;
    private final HotelMarketplaceService hotelService;
    private final BusinessConfigService businessConfig;

    @Override
    public ListingKind kind() {
        return ListingKind.STAY;
    }

    @Override
    public SubmitResultResponse materialize(Context context) {
        PartnerPlaceResponse place = createPlace(context);
        HotelProfileResponse hotel = hotelService.partnerCreateHotel(context.actorUserId(), hotelRequest(context, place.getId()));
        RoomTypeResponse room = hotelService.partnerCreateRoom(context.actorUserId(), hotel.getId(), roomRequest(context));
        RatePlanResponse rate = hotelService.partnerCreateRate(context.actorUserId(), room.getId(), rateRequest(context));
        applyWeekendUplift(context, rate);
        createPromotions(context, hotel.getId(), rate.getId());
        return SubmitResultResponse.builder()
                .draftId(context.draftId())
                .listingKind(kind())
                .hotelId(hotel.getId())
                .build();
    }

    // --- place ------------------------------------------------------------------------

    /**
     * A property must point at a row in {@code places}: that is what reviews, saved items and
     * the map all hang off. The partner place service either attaches to a catalogue place
     * within 50 m or creates one, so two hosts in the same building do not produce two pins.
     */
    private PartnerPlaceResponse createPlace(Context context) {
        StepData location = context.data().step(OnboardingSteps.STAY_LOCATION);
        UpsertPartnerPlaceRequest request = new UpsertPartnerPlaceRequest();
        request.setOrganizationId(context.organizationId());
        request.setTitle(listingTitle(context));
        request.setPlaceGroup("ACCOMMODATION");
        request.setAddress(location.text("address"));
        request.setLatitude(location.requiredDecimal("latitude"));
        request.setLongitude(location.requiredDecimal("longitude"));
        request.setTimezone(context.organizationTimezone());
        request.setDescription(context.data().step(OnboardingSteps.STAY_DESCRIPTION).text("description"));
        request.setThumbnail(coverPhoto(context));
        request.setImages(photoUrls(context));
        return partnerPlaceService.create(context.actorUserId(), request);
    }

    // --- property ---------------------------------------------------------------------

    private UpsertHotelRequest hotelRequest(Context context, java.util.UUID placeId) {
        StepData policies = context.data().step(OnboardingSteps.STAY_POLICIES);
        UpsertHotelRequest request = new UpsertHotelRequest();
        request.setOrganizationId(context.organizationId());
        request.setPlaceId(placeId);
        request.setPropertyType(propertyType(context));
        request.setDescription(context.data().step(OnboardingSteps.STAY_DESCRIPTION).text("description"));
        // Readiness requires both, and a property with no stated times cannot be sold; the
        // industry defaults are far better than refusing the submit over a skipped screen.
        request.setCheckInTime(timeOr(policies.time("checkInTime"), DEFAULT_CHECK_IN));
        request.setCheckOutTime(timeOr(policies.time("checkOutTime"), DEFAULT_CHECK_OUT));
        request.setTimezone(context.organizationTimezone());
        request.setAmenities(context.data().step(OnboardingSteps.STAY_AMENITIES).strings("amenities"));
        request.setImages(photoUrls(context));
        request.setHouseRules(houseRules(context));
        request.setBookingContact(bookingContact(policies));
        request.setPolicies(hotelPolicies(policies));
        // Created unsold on purpose: going on sale is a separate, deliberate act in the
        // workspace, where the readiness checklist says what is still missing.
        request.setStatus(MarketplacePublicationStatus.DRAFT);
        return request;
    }

    private HotelPropertyType propertyType(Context context) {
        String value = context.data().step(OnboardingSteps.STAY_PROPERTY_TYPE).text("propertyType");
        return enumOrDefault(HotelPropertyType.class, value, HotelPropertyType.HOTEL);
    }

    /**
     * House rules are the one place the wizard's answers become sentences a guest reads.
     * The lines are written by the client, which knows the partner's language; the server
     * does not compose guest-facing prose it would then have to translate.
     */
    private List<String> houseRules(Context context) {
        List<String> rules = new ArrayList<>(context.data().step(OnboardingSteps.STAY_POLICIES).strings("houseRules"));
        // The screens whose answer has no column of its own — who else lives there, whether the
        // bedrooms lock, whether the whole place is yours — send the sentence they want shown.
        rules.addAll(context.data().step(OnboardingSteps.STAY_WHO_ELSE).strings("houseRuleLines"));
        rules.addAll(context.data().step(OnboardingSteps.STAY_SPACE_TYPE).strings("houseRuleLines"));
        rules.addAll(context.data().step(OnboardingSteps.STAY_BASICS).strings("houseRuleLines"));
        return rules;
    }

    private Map<String, Object> bookingContact(StepData policies) {
        StepData contact = policies.object("contact");
        Map<String, Object> value = new LinkedHashMap<>();
        putIfPresent(value, "name", contact.text("name"));
        putIfPresent(value, "email", contact.text("email"));
        putIfPresent(value, "phone", contact.text("phone"));
        return value;
    }

    private HotelPolicies hotelPolicies(StepData policies) {
        return HotelPolicies.builder()
                .minCheckInAge(policies.integer("minCheckInAge"))
                .checkInInstructions(policies.text("checkInInstructions"))
                .childrenAllowed(policies.bool("childrenAllowed", true))
                .petsAllowed(policies.bool("petsAllowed", false))
                .smokingAllowed(policies.bool("smokingAllowed", false))
                .quietHoursStart(policies.text("quietHoursStart"))
                .quietHoursEnd(policies.text("quietHoursEnd"))
                .build();
    }

    // --- room -------------------------------------------------------------------------

    private UpsertRoomTypeRequest roomRequest(Context context) {
        StepData basics = context.data().step(OnboardingSteps.STAY_BASICS);
        StepData bathrooms = context.data().step(OnboardingSteps.STAY_BATHROOMS);
        int guests = Math.max(1, basics.integer("guests", 2));
        int beds = Math.max(1, basics.integer("beds", 1));

        UpsertRoomTypeRequest request = new UpsertRoomTypeRequest();
        request.setCode(DEFAULT_CODE);
        request.setName(listingTitle(context));
        request.setDescription(context.data().step(OnboardingSteps.STAY_DESCRIPTION).text("description"));
        request.setMaxAdults(guests);
        request.setStandardAdults(guests);
        request.setMaxOccupancy(guests);
        request.setMaxChildren(0);
        request.setMaxInfants(0);
        request.setBedroomCount(Math.max(0, basics.integer("bedrooms", 1)));
        request.setBathroomCount(bathroomCount(bathrooms));
        request.setBathroomType(bathroomType(bathrooms));
        request.setSmokingAllowed(context.data().step(OnboardingSteps.STAY_POLICIES).bool("smokingAllowed", false));
        request.setBedConfig(bedConfig(beds));
        request.setAmenities(context.data().step(OnboardingSteps.STAY_AMENITIES).strings("amenities"));
        request.setImages(photoUrls(context));
        // One unit, because the wizard describes one place to stay. A host with five identical
        // rooms raises this in the workspace, which also rebuilds the 730-day inventory.
        request.setTotalUnits(1);
        request.setStatus(MarketplaceAvailabilityStatus.ENABLED);
        return request;
    }

    /**
     * Airbnb counts half bathrooms, and so does the marketplace column — a toilet without a
     * shower is half a bathroom to everyone in hospitality. Summing the three kinds keeps the
     * "3.5 baths" a guest expects to read.
     */
    private BigDecimal bathroomCount(StepData bathrooms) {
        BigDecimal total = nullToZero(bathrooms.decimal("privateAttached"))
                .add(nullToZero(bathrooms.decimal("dedicated")))
                .add(nullToZero(bathrooms.decimal("shared")));
        return total.signum() > 0 ? total : BigDecimal.ONE;
    }

    private RoomBathroomType bathroomType(StepData bathrooms) {
        boolean shared = nullToZero(bathrooms.decimal("shared")).signum() > 0;
        return shared ? RoomBathroomType.SHARED : RoomBathroomType.PRIVATE;
    }

    private List<Map<String, Object>> bedConfig(int beds) {
        Map<String, Object> bed = new LinkedHashMap<>();
        bed.put("type", "DOUBLE");
        bed.put("count", beds);
        return List.of(bed);
    }

    // --- rate -------------------------------------------------------------------------

    private UpsertRatePlanRequest rateRequest(Context context) {
        StepData pricing = context.data().step(OnboardingSteps.STAY_PRICING);
        StepData policies = context.data().step(OnboardingSteps.STAY_POLICIES);
        UpsertRatePlanRequest request = new UpsertRatePlanRequest();
        request.setCode(DEFAULT_CODE);
        request.setName(pricing.text("rateName", "Standard"));
        request.setCurrency(pricing.text("currency", context.defaultCurrency()));
        request.setBasePrice(pricing.requiredDecimal("basePrice"));
        request.setBaseOccupancy(Math.max(1, context.data().step(OnboardingSteps.STAY_BASICS).integer("guests", 2)));
        request.setMealPlan(enumOrDefault(MealPlan.class, pricing.text("mealPlan"), MealPlan.ROOM_ONLY));
        request.setMinStay(1);
        request.setCancellationPolicy(cancellationPolicy(policies));
        request.setRefundable(policies.object("cancellation").bool("refundable", true));
        request.setStatus(MarketplaceAvailabilityStatus.ENABLED);
        return request;
    }

    /**
     * Readiness requires a cancellation policy with a type, and a listing without one cannot
     * be sold — so an unanswered policy screen becomes the friendliest real policy rather
     * than a blocked submit: free cancellation up to a day before arrival.
     */
    private Map<String, Object> cancellationPolicy(StepData policies) {
        StepData cancellation = policies.object("cancellation");
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", cancellation.text("type", "FREE_CANCELLATION"));
        value.put("freeCancellationHours", cancellation.integer("freeCancellationHours", 24));
        putIfPresent(value, "penaltyType", cancellation.text("penaltyType"));
        putIfPresent(value, "penaltyValue", cancellation.decimal("penaltyValue"));
        return value;
    }

    /**
     * A weekend uplift is a percentage on the screen and a price on the calendar. Writing it
     * as prices, not as a rule, is what makes it visible and editable in the month grid the
     * partner will actually manage it from.
     */
    private void applyWeekendUplift(Context context, RatePlanResponse rate) {
        StepData pricing = context.data().step(OnboardingSteps.STAY_PRICING);
        BigDecimal percent = pricing.decimal("weekendAdjustmentPercent");
        if (percent == null || percent.signum() <= 0) {
            return;
        }
        BigDecimal weekendPrice = pricing.requiredDecimal("basePrice")
                .multiply(BigDecimal.ONE.add(percent.divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(businessConfig.getInt(BusinessConfigKey.PARTNER_ONBOARDING_WEEKEND_HORIZON_DAYS));
        BulkUpdateRatePlanCalendarRequest request = new BulkUpdateRatePlanCalendarRequest();
        request.setStartDate(start);
        request.setEndDate(end);
        request.setDaysOfWeek(WEEKEND);
        request.setPrice(weekendPrice);
        request.setExpectedVersions(newCalendarVersions(start, end));
        hotelService.partnerUpdateRateCalendar(context.actorUserId(), rate.getId(), request);
    }

    /**
     * The calendar update is optimistically locked per date. Every date in a brand-new rate
     * plan is new, and the contract spells zero as "this date does not exist yet".
     */
    private Map<LocalDate, Long> newCalendarVersions(LocalDate start, LocalDate end) {
        Map<LocalDate, Long> versions = new HashMap<>();
        start.datesUntil(end.plusDays(1)).forEach(date -> versions.put(date, 0L));
        return versions;
    }

    // --- opening offers ----------------------------------------------------------------

    /**
     * The three deals the wizard offers map onto the promotion engine's existing levers, so
     * nothing new is needed to honour them: a launch window, a lead time, a length of stay.
     */
    private void createPromotions(Context context, java.util.UUID hotelId, java.util.UUID ratePlanId) {
        List<StepData> discounts = context.data().step(OnboardingSteps.STAY_DISCOUNTS).objects("discounts");
        LocalDate today = LocalDate.now();
        int index = 0;
        for (StepData discount : discounts) {
            BigDecimal percent = discount.decimal("percent");
            String type = discount.text("type");
            if (percent == null || percent.signum() <= 0 || type == null) {
                continue;
            }
            index++;
            UpsertRatePlanPromotionRequest request = new UpsertRatePlanPromotionRequest();
            request.setRatePlanId(ratePlanId);
            request.setCode(type + "-" + index);
            request.setName(discount.text("name", type));
            request.setDiscountPercent(percent);
            switch (type) {
                case "NEW_LISTING" -> {
                    request.setBookStart(today);
                    request.setBookEnd(today.plusDays(LIMITED_TIME_BOOKING_WINDOW_DAYS));
                }
                case "EARLY_BIRD" -> request.setMinAdvanceDays(EARLY_BIRD_ADVANCE_DAYS);
                case "WEEKLY" -> request.setMinNights(WEEKLY_MIN_NIGHTS);
                case "MONTHLY" -> request.setMinNights(MONTHLY_MIN_NIGHTS);
                default -> {
                    continue;
                }
            }
            hotelService.partnerCreatePromotion(context.actorUserId(), hotelId, request);
        }
    }

    // --- shared -----------------------------------------------------------------------

    private String listingTitle(Context context) {
        return context.data().step(OnboardingSteps.STAY_TITLE).requiredText("title");
    }

    private List<String> photoUrls(Context context) {
        return context.data().step(OnboardingSteps.STAY_PHOTOS).strings("photos");
    }

    private String coverPhoto(Context context) {
        List<String> photos = photoUrls(context);
        return photos.isEmpty() ? null : photos.get(0);
    }

    private static LocalTime timeOr(LocalTime value, LocalTime fallback) {
        return value == null ? fallback : value;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }
}
