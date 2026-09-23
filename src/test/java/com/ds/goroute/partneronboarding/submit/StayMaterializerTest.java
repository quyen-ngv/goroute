package com.ds.goroute.partneronboarding.submit;

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
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.partneronboarding.domain.DraftData;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import com.ds.goroute.partneronboarding.domain.OnboardingSteps;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.PartnerPlaceService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.HotelPropertyType;
import com.ds.goroute.type.MarketplacePublicationStatus;
import com.ds.goroute.type.RoomBathroomType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("StayMaterializer")
class StayMaterializerTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID DRAFT = UUID.randomUUID();
    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID PLACE = UUID.randomUUID();
    private static final UUID HOTEL = UUID.randomUUID();
    private static final UUID ROOM = UUID.randomUUID();
    private static final UUID RATE = UUID.randomUUID();

    private PartnerPlaceService places;
    private HotelMarketplaceService hotels;
    private StayMaterializer materializer;

    @BeforeEach
    void setUp() {
        places = mock(PartnerPlaceService.class);
        hotels = mock(HotelMarketplaceService.class);
        BusinessConfigService config = mock(BusinessConfigService.class);
        when(config.getInt(BusinessConfigKey.PARTNER_ONBOARDING_WEEKEND_HORIZON_DAYS)).thenReturn(365);

        when(places.create(any(), any())).thenReturn(PartnerPlaceResponse.builder().id(PLACE).build());
        when(hotels.partnerCreateHotel(any(), any())).thenReturn(HotelProfileResponse.builder().id(HOTEL).build());
        when(hotels.partnerCreateRoom(any(), any(), any())).thenReturn(RoomTypeResponse.builder().id(ROOM).build());
        when(hotels.partnerCreateRate(any(), any(), any())).thenReturn(RatePlanResponse.builder().id(RATE).build());

        materializer = new StayMaterializer(places, hotels, config);
    }

    @Test
    @DisplayName("creates a place, a property, one room and one rate, in that order")
    void createsTheWholeListing() {
        SubmitResultResponse result = materializer.materialize(context(completeAnswers()));

        assertThat(result.getHotelId()).isEqualTo(HOTEL);
        assertThat(result.getActivityId()).isNull();
        assertThat(result.getListingKind()).isEqualTo(ListingKind.STAY);
        verify(places).create(eq(ACTOR), any());
        verify(hotels).partnerCreateHotel(eq(ACTOR), any());
        verify(hotels).partnerCreateRoom(eq(ACTOR), eq(HOTEL), any());
        verify(hotels).partnerCreateRate(eq(ACTOR), eq(ROOM), any());
    }

    @Test
    @DisplayName("files the place under accommodation with the coordinates the host pinned")
    void placeCarriesTheLocation() {
        materializer.materialize(context(completeAnswers()));

        UpsertPartnerPlaceRequest request = capturePlace();
        assertThat(request.getPlaceGroup()).isEqualTo("ACCOMMODATION");
        assertThat(request.getOrganizationId()).isEqualTo(ORGANIZATION);
        assertThat(request.getLatitude()).isEqualByComparingTo("21.0285");
        assertThat(request.getLongitude()).isEqualByComparingTo("105.8542");
        assertThat(request.getTitle()).isEqualTo("Cosy loft by the lake");
    }

    @Test
    @DisplayName("creates the property unsold: going on sale is a separate, deliberate act")
    void propertyStartsAsADraft() {
        materializer.materialize(context(completeAnswers()));

        UpsertHotelRequest request = captureHotel();
        assertThat(request.getStatus()).isEqualTo(MarketplacePublicationStatus.DRAFT);
        assertThat(request.getPlaceId()).isEqualTo(PLACE);
        assertThat(request.getPropertyType()).isEqualTo(HotelPropertyType.APARTMENT);
        assertThat(request.getCheckInTime()).isEqualTo(LocalTime.of(15, 0));
        assertThat(request.getCheckOutTime()).isEqualTo(LocalTime.of(11, 0));
        assertThat(request.getBookingContact()).containsEntry("phone", "+84 90 000 0000");
    }

    @Test
    @DisplayName("falls back to industry check-in times rather than refusing a skipped screen")
    void fillsMissingCheckInTimes() {
        Map<String, Object> answers = completeAnswers();
        answers.put(OnboardingSteps.STAY_POLICIES, Map.of("contact", Map.of("phone", "+84 90 000 0000")));

        materializer.materialize(context(answers));

        UpsertHotelRequest request = captureHotel();
        assertThat(request.getCheckInTime()).isEqualTo(LocalTime.of(14, 0));
        assertThat(request.getCheckOutTime()).isEqualTo(LocalTime.of(12, 0));
    }

    @Test
    @DisplayName("collects the house rules the screens without a column of their own sent")
    void gathersHouseRulesFromEveryScreen() {
        materializer.materialize(context(completeAnswers()));

        assertThat(captureHotel().getHouseRules())
                .containsExactly("No parties", "The host lives here", "Every bedroom locks");
    }

    @Test
    @DisplayName("counts half bathrooms and marks the room shared when any bathroom is")
    void sumsBathroomsIncludingHalves() {
        materializer.materialize(context(completeAnswers()));

        UpsertRoomTypeRequest request = captureRoom();
        assertThat(request.getBathroomCount()).isEqualByComparingTo("2.5");
        assertThat(request.getBathroomType()).isEqualTo(RoomBathroomType.SHARED);
    }

    @Test
    @DisplayName("sizes the single room from the basics screen")
    void roomMatchesTheBasics() {
        materializer.materialize(context(completeAnswers()));

        UpsertRoomTypeRequest request = captureRoom();
        assertThat(request.getMaxAdults()).isEqualTo(4);
        assertThat(request.getMaxOccupancy()).isEqualTo(4);
        assertThat(request.getBedroomCount()).isEqualTo(3);
        assertThat(request.getTotalUnits()).isEqualTo(1);
        assertThat(request.getBedConfig()).singleElement()
                .satisfies(bed -> assertThat(bed).containsEntry("count", 3));
    }

    @Test
    @DisplayName("gives an unanswered cancellation screen the friendliest real policy")
    void defaultsToFreeCancellation() {
        Map<String, Object> answers = completeAnswers();
        answers.put(OnboardingSteps.STAY_POLICIES, Map.of("contact", Map.of("phone", "+84 90 000 0000")));

        materializer.materialize(context(answers));

        assertThat(captureRate().getCancellationPolicy())
                .containsEntry("type", "FREE_CANCELLATION")
                .containsEntry("freeCancellationHours", 24);
    }

    @Test
    @DisplayName("writes the weekend uplift as Friday and Saturday prices on the calendar")
    void writesWeekendPrices() {
        materializer.materialize(context(completeAnswers()));

        ArgumentCaptor<BulkUpdateRatePlanCalendarRequest> captor =
                ArgumentCaptor.forClass(BulkUpdateRatePlanCalendarRequest.class);
        verify(hotels).partnerUpdateRateCalendar(eq(ACTOR), eq(RATE), captor.capture());
        BulkUpdateRatePlanCalendarRequest request = captor.getValue();

        assertThat(request.getDaysOfWeek()).containsExactlyInAnyOrder(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
        assertThat(request.getPrice()).isEqualByComparingTo("1050000.00");
        assertThat(request.getExpectedVersions().values()).containsOnly(0L);
        assertThat(request.getExpectedVersions())
                .as("every selected date must be present, or the update is refused")
                .containsKey(request.getStartDate())
                .containsKey(request.getEndDate());
    }

    @Test
    @DisplayName("touches no calendar when the host set no weekend uplift")
    void skipsCalendarWithoutAnUplift() {
        Map<String, Object> answers = completeAnswers();
        answers.put(OnboardingSteps.STAY_PRICING, Map.of("basePrice", 1_000_000, "currency", "VND"));

        materializer.materialize(context(answers));

        verify(hotels, never()).partnerUpdateRateCalendar(any(), any(), any());
    }

    @Test
    @DisplayName("maps each opening deal onto the promotion lever that expresses it")
    void mapsDiscountsToPromotions() {
        materializer.materialize(context(completeAnswers()));

        ArgumentCaptor<UpsertRatePlanPromotionRequest> captor =
                ArgumentCaptor.forClass(UpsertRatePlanPromotionRequest.class);
        verify(hotels, org.mockito.Mockito.times(2))
                .partnerCreatePromotion(eq(ACTOR), eq(HOTEL), captor.capture());
        List<UpsertRatePlanPromotionRequest> promotions = captor.getAllValues();

        assertThat(promotions).allSatisfy(promotion -> assertThat(promotion.getRatePlanId()).isEqualTo(RATE));
        assertThat(promotions.get(0).getBookEnd())
                .as("a launch deal is a booking window, not a stay window")
                .isEqualTo(promotions.get(0).getBookStart().plusDays(90));
        assertThat(promotions.get(1).getMinAdvanceDays()).isEqualTo(14);
    }

    @Test
    @DisplayName("ignores a discount row with no percentage rather than creating a 0% promotion")
    void ignoresEmptyDiscountRows() {
        Map<String, Object> answers = completeAnswers();
        answers.put(OnboardingSteps.STAY_DISCOUNTS, Map.of("discounts",
                List.of(Map.of("type", "EARLY_BIRD"), Map.of("percent", 10), Map.of("type", "MYSTERY", "percent", 5))));

        materializer.materialize(context(answers));

        verify(hotels, never()).partnerCreatePromotion(any(), any(), any());
    }

    @Test
    @DisplayName("names the step and field when something a listing cannot exist without is missing")
    void namesTheMissingStep() {
        Map<String, Object> answers = completeAnswers();
        answers.remove(OnboardingSteps.STAY_TITLE);

        assertThatThrownBy(() -> materializer.materialize(context(answers)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(OnboardingSteps.STAY_TITLE)
                .hasMessageContaining("title");
    }

    // --- fixtures -----------------------------------------------------------------------

    private ListingMaterializer.Context context(Map<String, Object> answers) {
        return new ListingMaterializer.Context(ACTOR, DRAFT, ORGANIZATION, "Asia/Ho_Chi_Minh", "VND",
                DraftData.of(answers));
    }

    /** A host who answered every screen, so each test can remove exactly the one it is about. */
    private Map<String, Object> completeAnswers() {
        Map<String, Object> answers = new HashMap<>();
        answers.put(OnboardingSteps.STAY_PROPERTY_TYPE, Map.of("propertyType", "APARTMENT"));
        answers.put(OnboardingSteps.STAY_LOCATION, Map.of(
                "address", "12 Hang Bac", "latitude", new BigDecimal("21.0285"), "longitude", new BigDecimal("105.8542")));
        answers.put(OnboardingSteps.STAY_SPACE_TYPE, Map.of(
                "spaceType", "PRIVATE_ROOM", "houseRuleLines", List.of("The host lives here")));
        answers.put(OnboardingSteps.STAY_BASICS, Map.of(
                "guests", 4, "bedrooms", 3, "beds", 3, "houseRuleLines", List.of("Every bedroom locks")));
        answers.put(OnboardingSteps.STAY_BATHROOMS, Map.of(
                "privateAttached", 1, "dedicated", 0.5, "shared", 1));
        answers.put(OnboardingSteps.STAY_AMENITIES, Map.of("amenities", List.of("WIFI", "AIR_CONDITIONING")));
        answers.put(OnboardingSteps.STAY_PHOTOS, Map.of("photos",
                List.of("https://cdn/1.jpg", "https://cdn/2.jpg")));
        answers.put(OnboardingSteps.STAY_TITLE, Map.of("title", "Cosy loft by the lake"));
        answers.put(OnboardingSteps.STAY_DESCRIPTION, Map.of("description", "A quiet loft two minutes from the water."));
        answers.put(OnboardingSteps.STAY_PRICING, Map.of(
                "basePrice", 1_000_000, "currency", "VND", "weekendAdjustmentPercent", 5));
        answers.put(OnboardingSteps.STAY_DISCOUNTS, Map.of("discounts", List.of(
                Map.of("type", "NEW_LISTING", "percent", 5),
                Map.of("type", "EARLY_BIRD", "percent", 20))));
        answers.put(OnboardingSteps.STAY_POLICIES, Map.of(
                "checkInTime", "15:00", "checkOutTime", "11:00",
                "houseRules", List.of("No parties"),
                "contact", Map.of("phone", "+84 90 000 0000", "email", "host@example.com"),
                "cancellation", Map.of("type", "FLEXIBLE", "freeCancellationHours", 48)));
        return answers;
    }

    private UpsertPartnerPlaceRequest capturePlace() {
        ArgumentCaptor<UpsertPartnerPlaceRequest> captor = ArgumentCaptor.forClass(UpsertPartnerPlaceRequest.class);
        verify(places).create(eq(ACTOR), captor.capture());
        return captor.getValue();
    }

    private UpsertHotelRequest captureHotel() {
        ArgumentCaptor<UpsertHotelRequest> captor = ArgumentCaptor.forClass(UpsertHotelRequest.class);
        verify(hotels).partnerCreateHotel(eq(ACTOR), captor.capture());
        return captor.getValue();
    }

    private UpsertRoomTypeRequest captureRoom() {
        ArgumentCaptor<UpsertRoomTypeRequest> captor = ArgumentCaptor.forClass(UpsertRoomTypeRequest.class);
        verify(hotels).partnerCreateRoom(eq(ACTOR), eq(HOTEL), captor.capture());
        return captor.getValue();
    }

    private UpsertRatePlanRequest captureRate() {
        ArgumentCaptor<UpsertRatePlanRequest> captor = ArgumentCaptor.forClass(UpsertRatePlanRequest.class);
        verify(hotels).partnerCreateRate(eq(ACTOR), eq(ROOM), captor.capture());
        return captor.getValue();
    }
}
