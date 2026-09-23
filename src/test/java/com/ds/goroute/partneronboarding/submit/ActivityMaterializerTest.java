package com.ds.goroute.partneronboarding.submit;

import com.ds.goroute.dto.request.BulkCreateActivitySlotsRequest;
import com.ds.goroute.dto.request.UpsertActivityPackageRequest;
import com.ds.goroute.dto.request.UpsertMarketplaceActivityRequest;
import com.ds.goroute.dto.response.ActivityPackageResponse;
import com.ds.goroute.dto.response.MarketplaceActivityResponse;
import com.ds.goroute.partneronboarding.domain.DraftData;
import com.ds.goroute.partneronboarding.domain.OnboardingSteps;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.ActivityGroupType;
import com.ds.goroute.type.ActivityProductType;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MarketplacePublicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The two branches that write a product, packages and slots. Tested together because what
 * matters most is that they agree on the shape they produce.
 */
class ActivityMaterializerTest {

    private static final UUID ACTOR = UUID.randomUUID();
    private static final UUID DRAFT = UUID.randomUUID();
    private static final UUID ORGANIZATION = UUID.randomUUID();
    private static final UUID PRODUCT = UUID.randomUUID();

    private ActivityCommerceService activities;
    private BusinessConfigService config;

    @BeforeEach
    void setUp() {
        activities = mock(ActivityCommerceService.class);
        config = mock(BusinessConfigService.class);
        when(config.getInt(BusinessConfigKey.PARTNER_ONBOARDING_SLOT_HORIZON_DAYS)).thenReturn(14);
        when(activities.partnerCreate(any(), any()))
                .thenReturn(MarketplaceActivityResponse.builder().id(PRODUCT).build());
        when(activities.partnerCreatePackage(any(), any(), any()))
                .thenAnswer(invocation -> ActivityPackageResponse.builder().id(UUID.randomUUID()).build());
    }

    @Nested
    @DisplayName("ExperienceMaterializer")
    class Experience {

        @Test
        @DisplayName("creates the product unsold, with the itinerary and the meeting point")
        void createsTheProduct() {
            SubmitResultResponse result = new ExperienceMaterializer(activities, config)
                    .materialize(context(experienceAnswers()));

            assertThat(result.getActivityId()).isEqualTo(PRODUCT);
            assertThat(result.getHotelId()).isNull();

            UpsertMarketplaceActivityRequest product = captureProduct();
            assertThat(product.getProductStatus()).isEqualTo(MarketplacePublicationStatus.DRAFT);
            assertThat(product.getActivityType()).isEqualTo(ActivityProductType.TOUR);
            assertThat(product.getMeetingPoint()).isEqualTo("Old Quarter gate");
            assertThat(product.getDestinations()).containsExactly("Hanoi");
            assertThat(product.getVisitDurationMinutes())
                    .as("the length of the experience is the sum of its stops")
                    .isEqualTo(150);
            assertThat(product.getItinerary()).extracting("title")
                    .containsExactly("Meet and walk", "Street food stop");
        }

        @Test
        @DisplayName("reads a workshop as a class and everything else as a tour")
        void derivesTheProductTypeFromTheSubtype() {
            Map<String, Object> answers = experienceAnswers();
            answers.put(OnboardingSteps.EXPERIENCE_SUBTYPE, Map.of("subtype", "ART_WORKSHOP"));

            new ExperienceMaterializer(activities, config).materialize(context(answers));

            assertThat(captureProduct().getActivityType()).isEqualTo(ActivityProductType.CLASS);
        }

        @Test
        @DisplayName("sells a join-in seat and, when a minimum was given, the whole experience")
        void createsBothPackagesWhenAPrivateMinimumExists() {
            new ExperienceMaterializer(activities, config).materialize(context(experienceAnswers()));

            List<UpsertActivityPackageRequest> packages = capturePackages(2);
            assertThat(packages.get(0).getGroupType()).isEqualTo(ActivityGroupType.JOIN_IN);
            assertThat(packages.get(0).getBasePrice()).isEqualByComparingTo("500000");
            assertThat(packages.get(0).getUnits()).singleElement()
                    .satisfies(unit -> assertThat(unit.getPrice()).isEqualByComparingTo("500000"));

            assertThat(packages.get(1).getGroupType()).isEqualTo(ActivityGroupType.PRIVATE);
            assertThat(packages.get(1).getBasePrice()).isEqualByComparingTo("3000000");
            assertThat(packages.get(1).getMaxQuantity())
                    .as("a private booking is one line at the minimum, not a per-head count")
                    .isEqualTo(1);
        }

        @Test
        @DisplayName("sells only the join-in seat when no private minimum was given")
        void skipsThePrivatePackageWithoutAMinimum() {
            Map<String, Object> answers = experienceAnswers();
            answers.put(OnboardingSteps.EXPERIENCE_PRICING, Map.of("pricePerGuest", 500_000));

            new ExperienceMaterializer(activities, config).materialize(context(answers));

            assertThat(capturePackages(1)).hasSize(1);
        }

        @Test
        @DisplayName("keeps the wizard's category on the package, which has a place for it")
        void keepsTheClassificationOnThePackage() {
            new ExperienceMaterializer(activities, config).materialize(context(experienceAnswers()));

            assertThat(capturePackages(2).get(0).getAttributes())
                    .containsEntry("category", "FOOD_DRINK")
                    .containsEntry("subtype", "WALKING_TOUR");
        }

        @Test
        @DisplayName("opens departures on the days and times the schedule screen listed")
        void generatesDeparturesFromTheWeeklySchedule() {
            new ExperienceMaterializer(activities, config).materialize(context(experienceAnswers()));

            BulkCreateActivitySlotsRequest slots = captureSlots();
            assertThat(slots.getSlots()).isNotEmpty();
            assertThat(slots.getSlots()).allSatisfy(slot -> {
                assertThat(slot.getStartsAt().getDayOfWeek()).isIn(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
                assertThat(slot.getStartsAt().toLocalTime()).isEqualTo(LocalTime.of(8, 30));
                assertThat(slot.getTimezone()).isEqualTo("Asia/Ho_Chi_Minh");
                assertThat(slot.getCapacity()).isEqualTo(8);
                assertThat(slot.getEndsAt()).isEqualTo(slot.getStartsAt().plusMinutes(150));
            });
        }

        @Test
        @DisplayName("creates no departures when the partner listed no schedule")
        void createsNoSlotsWithoutASchedule() {
            Map<String, Object> answers = experienceAnswers();
            answers.remove(OnboardingSteps.EXPERIENCE_SCHEDULE);

            new ExperienceMaterializer(activities, config).materialize(context(answers));

            verify(activities, never()).partnerCreateSlots(any(), any(), any());
        }
    }

    @Nested
    @DisplayName("ServiceMaterializer")
    class Service {

        @Test
        @DisplayName("creates one package per offering, priced per guest or per group")
        void createsOnePackagePerOffering() {
            new ServiceMaterializer(activities, config).materialize(context(serviceAnswers()));

            List<UpsertActivityPackageRequest> packages = capturePackages(2);
            assertThat(packages).extracting(UpsertActivityPackageRequest::getName)
                    .containsExactly("Deep tissue 60", "Couples package");

            assertThat(packages.get(0).getUnits())
                    .as("per guest means the counter multiplies the price")
                    .hasSize(1);
            assertThat(packages.get(1).getUnits())
                    .as("per group is one line whatever the party size")
                    .isEmpty();
        }

        @Test
        @DisplayName("advertises the cheapest offering as the listing's starting price")
        void productPriceIsTheCheapestOffering() {
            new ServiceMaterializer(activities, config).materialize(context(serviceAnswers()));

            assertThat(captureProduct().getPriceAmount()).isEqualByComparingTo("450000");
        }

        @Test
        @DisplayName("divides the opening hours by each offering's own length")
        void slotsFollowEachOfferingsLength() {
            new ServiceMaterializer(activities, config).materialize(context(serviceAnswers()));

            ArgumentCaptor<BulkCreateActivitySlotsRequest> captor =
                    ArgumentCaptor.forClass(BulkCreateActivitySlotsRequest.class);
            verify(activities, times(2)).partnerCreateSlots(eq(ACTOR), any(), captor.capture());

            List<LocalTime> sixtyMinuteStarts = captor.getAllValues().get(0).getSlots().stream()
                    .filter(slot -> slot.getStartsAt().getDayOfWeek() == DayOfWeek.MONDAY)
                    .map(slot -> slot.getStartsAt().toLocalTime())
                    .toList();
            List<LocalTime> ninetyMinuteStarts = captor.getAllValues().get(1).getSlots().stream()
                    .filter(slot -> slot.getStartsAt().getDayOfWeek() == DayOfWeek.MONDAY)
                    .map(slot -> slot.getStartsAt().toLocalTime())
                    .toList();

            assertThat(sixtyMinuteStarts).startsWith(LocalTime.of(9, 0), LocalTime.of(10, 0));
            assertThat(ninetyMinuteStarts).startsWith(LocalTime.of(9, 0), LocalTime.of(10, 30));
        }

        @Test
        @DisplayName("keeps the hours, the travel radius and the price unit where they can be read back")
        void keepsWhatTheModelHasNoColumnFor() {
            new ServiceMaterializer(activities, config).materialize(context(serviceAnswers()));

            Map<String, Object> attributes = capturePackages(2).get(0).getAttributes();
            assertThat(attributes).containsEntry("priceUnit", "PER_GUEST");
            assertThat(attributes).containsEntry("serviceType", "MASSAGE");
            assertThat(attributes).containsKey("businessHours");
            assertThat(attributes.get("serviceModes")).isEqualTo(List.of("TRAVEL_TO_GUESTS", "GUESTS_COME_TO_YOU"));
            assertThat(attributes.get("serviceArea")).asInstanceOf(
                            org.assertj.core.api.InstanceOfAssertFactories.MAP)
                    .containsEntry("maxDriveMinutes", 30);
        }

        @Test
        @DisplayName("shows the meeting place to guests and states the travel radius in words")
        void showsBothWaysOfBeingServed() {
            new ServiceMaterializer(activities, config).materialize(context(serviceAnswers()));

            UpsertMarketplaceActivityRequest product = captureProduct();
            assertThat(product.getMeetingPoint()).isEqualTo("5 Ly Thuong Kiet");
            assertThat(product.getGoodToKnow()).containsExactly("We travel up to 30 minutes from the studio");
        }
    }

    // --- fixtures ---------------------------------------------------------------------

    private ListingMaterializer.Context context(Map<String, Object> answers) {
        return new ListingMaterializer.Context(ACTOR, DRAFT, ORGANIZATION, "Asia/Ho_Chi_Minh", "VND",
                DraftData.of(answers));
    }

    private Map<String, Object> experienceAnswers() {
        Map<String, Object> answers = new HashMap<>();
        answers.put(OnboardingSteps.EXPERIENCE_CATEGORY, Map.of("category", "FOOD_DRINK"));
        answers.put(OnboardingSteps.EXPERIENCE_CITY, Map.of("name", "Hanoi"));
        answers.put(OnboardingSteps.EXPERIENCE_SUBTYPE, Map.of("subtype", "WALKING_TOUR"));
        answers.put(OnboardingSteps.EXPERIENCE_LOCATION, Map.of(
                "address", "Old Quarter gate", "latitude", 21.03, "longitude", 105.85));
        answers.put(OnboardingSteps.EXPERIENCE_PHOTOS, Map.of("photos", List.of("https://cdn/a.jpg")));
        answers.put(OnboardingSteps.EXPERIENCE_ITINERARY, Map.of("activities", List.of(
                Map.of("title", "Meet and walk", "durationMinutes", 30),
                Map.of("title", "Street food stop", "durationMinutes", 120))));
        answers.put(OnboardingSteps.EXPERIENCE_CAPACITY, Map.of("maxGuests", 8));
        answers.put(OnboardingSteps.EXPERIENCE_PRICING, Map.of(
                "pricePerGuest", 500_000, "privateGroupMinimum", 3_000_000));
        answers.put(OnboardingSteps.EXPERIENCE_INCLUDED, Map.of("included", List.of("Light bites")));
        answers.put(OnboardingSteps.EXPERIENCE_SCHEDULE, Map.of("weekly", List.of(
                Map.of("dayOfWeek", "SATURDAY", "startTimes", List.of("08:30")),
                Map.of("dayOfWeek", "SUNDAY", "startTimes", List.of("08:30")))));
        answers.put(OnboardingSteps.EXPERIENCE_TITLE_DESCRIPTION, Map.of(
                "title", "Hanoi street food walk", "description", "Eat your way through the Old Quarter."));
        return answers;
    }

    private Map<String, Object> serviceAnswers() {
        Map<String, Object> answers = new HashMap<>();
        answers.put(OnboardingSteps.SERVICE_CATEGORY, Map.of("category", "SPA_TREATMENTS"));
        answers.put(OnboardingSteps.SERVICE_CITY, Map.of("name", "Hanoi"));
        answers.put(OnboardingSteps.SERVICE_LOCATION_MODE, Map.of(
                "modes", List.of("TRAVEL_TO_GUESTS", "GUESTS_COME_TO_YOU"),
                "meetingPlace", Map.of("address", "5 Ly Thuong Kiet", "latitude", 21.02, "longitude", 105.84),
                "serviceArea", Map.of("startAddress", "5 Ly Thuong Kiet", "maxDriveMinutes", 30),
                "goodToKnowLines", List.of("We travel up to 30 minutes from the studio")));
        answers.put(OnboardingSteps.SERVICE_OFFERINGS, Map.of("offerings", List.of(
                Map.of("title", "Deep tissue 60", "price", 450_000, "priceUnit", "PER_GUEST",
                        "serviceType", "MASSAGE", "durationMinutes", 60, "maxGuests", 1),
                Map.of("title", "Couples package", "price", 900_000, "priceUnit", "PER_GROUP",
                        "serviceType", "MASSAGE", "durationMinutes", 90, "maxGuests", 2))));
        answers.put(OnboardingSteps.SERVICE_BUSINESS_HOURS, Map.of("ranges", List.of(
                Map.of("days", List.of("MONDAY", "TUESDAY"), "from", "09:00", "to", "17:00"))));
        answers.put(OnboardingSteps.SERVICE_TITLE_DESCRIPTION, Map.of(
                "title", "Lotus Spa", "description", "Treatments at our studio or at your hotel."));
        return answers;
    }

    private UpsertMarketplaceActivityRequest captureProduct() {
        ArgumentCaptor<UpsertMarketplaceActivityRequest> captor =
                ArgumentCaptor.forClass(UpsertMarketplaceActivityRequest.class);
        verify(activities).partnerCreate(eq(ACTOR), captor.capture());
        return captor.getValue();
    }

    private List<UpsertActivityPackageRequest> capturePackages(int expected) {
        ArgumentCaptor<UpsertActivityPackageRequest> captor =
                ArgumentCaptor.forClass(UpsertActivityPackageRequest.class);
        verify(activities, times(expected)).partnerCreatePackage(eq(ACTOR), eq(PRODUCT), captor.capture());
        return captor.getAllValues();
    }

    private BulkCreateActivitySlotsRequest captureSlots() {
        ArgumentCaptor<BulkCreateActivitySlotsRequest> captor =
                ArgumentCaptor.forClass(BulkCreateActivitySlotsRequest.class);
        verify(activities, org.mockito.Mockito.atLeastOnce())
                .partnerCreateSlots(eq(ACTOR), any(), captor.capture());
        return captor.getValue();
    }
}
