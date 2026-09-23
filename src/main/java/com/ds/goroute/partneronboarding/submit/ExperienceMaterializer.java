package com.ds.goroute.partneronboarding.submit;

import com.ds.goroute.dto.ActivityItineraryItem;
import com.ds.goroute.dto.ActivityPackageDetails;
import com.ds.goroute.dto.ActivityPackageUnit;
import com.ds.goroute.dto.GeoCoordinateDto;
import com.ds.goroute.dto.request.UpsertActivityPackageRequest;
import com.ds.goroute.dto.request.UpsertMarketplaceActivityRequest;
import com.ds.goroute.partneronboarding.domain.ListingKind;
import com.ds.goroute.partneronboarding.domain.OnboardingSteps;
import com.ds.goroute.partneronboarding.domain.StepData;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.ActivityDepartureType;
import com.ds.goroute.type.ActivityGroupType;
import com.ds.goroute.type.ActivityProductType;
import com.ds.goroute.type.ActivityUnitType;
import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import com.ds.goroute.type.MarketplaceConfirmationType;
import com.ds.goroute.type.MarketplacePublicationStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * An experience: one product, a shared departure and optionally a private one, running on a
 * weekly schedule.
 *
 * <p>The two packages come from the two prices the wizard asks for. "Price per guest" is the
 * join-in departure anyone can book a seat on; "private group minimum" is the same experience
 * bought whole, which is a different product to a guest and therefore a different package —
 * not a discount rule on the first one.
 */
@Component
public class ExperienceMaterializer extends ActivityMaterializerSupport {

    private static final String JOIN_IN_CODE = "JOIN";
    private static final String PRIVATE_CODE = "PRIVATE";
    private static final int DEFAULT_DURATION_MINUTES = 60;

    public ExperienceMaterializer(ActivityCommerceService activityService, BusinessConfigService businessConfig) {
        super(activityService, businessConfig);
    }

    @Override
    public ListingKind kind() {
        return ListingKind.EXPERIENCE;
    }

    @Override
    protected UpsertMarketplaceActivityRequest product(Context context) {
        StepData titleStep = context.data().step(OnboardingSteps.EXPERIENCE_TITLE_DESCRIPTION);
        StepData location = context.data().step(OnboardingSteps.EXPERIENCE_LOCATION);
        StepData photos = context.data().step(OnboardingSteps.EXPERIENCE_PHOTOS);

        UpsertMarketplaceActivityRequest request = new UpsertMarketplaceActivityRequest();
        request.setOrganizationId(context.organizationId());
        request.setActivityType(activityType(context));
        request.setTitle(titleStep.requiredText("title"));
        request.setDescription(titleStep.text("description"));
        request.setMeetingPoint(location.text("address"));
        request.setActivityAddress(location.text("address"));
        applyCity(request, context.data().step(OnboardingSteps.EXPERIENCE_CITY));
        applyCoordinates(request, location);
        request.setIncludedItems(context.data().step(OnboardingSteps.EXPERIENCE_INCLUDED).strings("included"));
        request.setItinerary(itinerary(context));
        request.setThumbnail(coverPhoto(photos));
        request.setImages(photos.strings("photos"));
        request.setPriceCurrency(currency(context));
        request.setPriceAmount(pricePerGuest(context));
        request.setVisitDurationMinutes(totalDurationMinutes(context));
        request.setCancellationPolicy(cancellationPolicy(context.data().step(OnboardingSteps.EXPERIENCE_POLICIES)));
        // Created unsold; the workspace's readiness checklist owns going on sale.
        request.setProductStatus(MarketplacePublicationStatus.DRAFT);
        return request;
    }

    /**
     * The category screen is Airbnb's vocabulary, not the marketplace's. Only the coarse
     * distinction the product model actually has is derived here — a workshop is a class, a
     * walk is a tour — and the finer label is kept on the package where it is displayed.
     */
    private ActivityProductType activityType(Context context) {
        String subtype = context.data().step(OnboardingSteps.EXPERIENCE_SUBTYPE).text("subtype");
        if (subtype == null) {
            return ActivityProductType.TOUR;
        }
        String normalized = subtype.toUpperCase();
        if (normalized.contains("WORKSHOP") || normalized.contains("CLASS") || normalized.contains("LESSON")) {
            return ActivityProductType.CLASS;
        }
        return ActivityProductType.TOUR;
    }

    private void applyCoordinates(UpsertMarketplaceActivityRequest request, StepData location) {
        BigDecimal latitude = location.decimal("latitude");
        BigDecimal longitude = location.decimal("longitude");
        if (latitude != null && longitude != null) {
            GeoCoordinateDto coordinate = new GeoCoordinateDto();
            coordinate.setLat(latitude);
            coordinate.setLng(longitude);
            request.setDestinationCoordinates(List.of(coordinate));
        }
    }

    private List<ActivityItineraryItem> itinerary(Context context) {
        List<ActivityItineraryItem> items = new ArrayList<>();
        for (StepData activity : context.data().step(OnboardingSteps.EXPERIENCE_ITINERARY).objects("activities")) {
            String title = activity.text("title");
            if (title == null) {
                continue;
            }
            ActivityItineraryItem item = new ActivityItineraryItem();
            item.setTitle(title);
            item.setContent(activity.text("description"));
            items.add(item);
        }
        return items;
    }

    @Override
    protected List<PackagePlan> packages(Context context) {
        StepData pricing = context.data().step(OnboardingSteps.EXPERIENCE_PRICING);
        int capacity = Math.max(1, context.data().step(OnboardingSteps.EXPERIENCE_CAPACITY).integer("maxGuests", 10));
        int duration = totalDurationMinutes(context);
        List<SlotSchedulePlanner.PlannedSlot> slots = plannedSlots(context, duration);
        String timezone = timezone(context);

        List<PackagePlan> plans = new ArrayList<>(2);
        plans.add(new PackagePlan(joinInPackage(context, pricing, capacity, duration), slots, capacity, timezone));

        BigDecimal privateMinimum = pricing.decimal("privateGroupMinimum");
        if (privateMinimum != null && privateMinimum.signum() > 0) {
            plans.add(new PackagePlan(privatePackage(context, privateMinimum, capacity, duration),
                    slots, capacity, timezone));
        }
        return plans;
    }

    private UpsertActivityPackageRequest joinInPackage(Context context, StepData pricing, int capacity, int duration) {
        BigDecimal price = pricePerGuest(context);
        UpsertActivityPackageRequest request = basePackage(context, JOIN_IN_CODE,
                pricing.text("joinInName", "Per guest"), price, capacity, duration);
        request.setGroupType(ActivityGroupType.JOIN_IN);
        request.setUnits(List.of(unit(price)));
        return request;
    }

    private UpsertActivityPackageRequest privatePackage(Context context, BigDecimal minimum, int capacity, int duration) {
        UpsertActivityPackageRequest request = basePackage(context, PRIVATE_CODE,
                "Private group", minimum, capacity, duration);
        request.setGroupType(ActivityGroupType.PRIVATE);
        // Priced whole rather than per head: a private booking is one line at the minimum.
        request.setMaxQuantity(1);
        return request;
    }

    private UpsertActivityPackageRequest basePackage(Context context, String code, String name,
                                                     BigDecimal price, int capacity, int duration) {
        StepData included = context.data().step(OnboardingSteps.EXPERIENCE_INCLUDED);
        UpsertActivityPackageRequest request = new UpsertActivityPackageRequest();
        request.setCode(code);
        request.setName(name);
        request.setCurrency(currency(context));
        request.setBasePrice(price);
        request.setMinQuantity(1);
        request.setMaxQuantity(capacity);
        request.setIncludedItems(included.strings("included"));
        request.setConfirmationType(confirmationType(context));
        request.setDepartureType(departureType(context));
        request.setCancellationPolicy(cancellationPolicy(context.data().step(OnboardingSteps.EXPERIENCE_POLICIES)));
        request.setDetails(packageDetails(context, duration));
        request.setAttributes(classification(context));
        request.setStatus(MarketplaceAvailabilityStatus.ENABLED);
        return request;
    }

    private ActivityPackageUnit unit(BigDecimal price) {
        ActivityPackageUnit unit = new ActivityPackageUnit();
        unit.setCode("ADULT");
        unit.setName("Adult");
        unit.setUnitType(ActivityUnitType.ADULT);
        unit.setPrice(price);
        unit.setPaxCount(1);
        return unit;
    }

    /**
     * The wizard's category and subtype have no column of their own on the product, and the
     * package already carries a free-form {@code attributes} map that survives a round trip.
     * Keeping them means a later screen can group or filter by them without a migration now.
     */
    private Map<String, Object> classification(Context context) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "category", context.data().step(OnboardingSteps.EXPERIENCE_CATEGORY).text("category"));
        putIfPresent(attributes, "subtype", context.data().step(OnboardingSteps.EXPERIENCE_SUBTYPE).text("subtype"));
        return attributes;
    }

    private ActivityPackageDetails packageDetails(Context context, int duration) {
        ActivityPackageDetails details = new ActivityPackageDetails();
        details.setDurationMinutes(duration);
        details.setAreaName(context.data().step(OnboardingSteps.EXPERIENCE_CITY).text("name"));
        details.setItinerary(itineraryStops(context));
        return details;
    }

    private List<ActivityPackageDetails.ItineraryStop> itineraryStops(Context context) {
        List<ActivityPackageDetails.ItineraryStop> stops = new ArrayList<>();
        for (StepData activity : context.data().step(OnboardingSteps.EXPERIENCE_ITINERARY).objects("activities")) {
            String title = activity.text("title");
            if (title == null) {
                continue;
            }
            ActivityPackageDetails.ItineraryStop stop = new ActivityPackageDetails.ItineraryStop();
            stop.setTitle(title);
            stop.setDescription(activity.text("description"));
            stop.setDurationMinutes(activity.integer("durationMinutes"));
            stops.add(stop);
        }
        return stops;
    }

    // --- schedule -----------------------------------------------------------------------

    /**
     * The schedule screen collects a weekly pattern — "Tuesdays and Thursdays at 09:00" — and
     * the planner turns it into real departures for the configured horizon.
     */
    private List<SlotSchedulePlanner.PlannedSlot> plannedSlots(Context context, int duration) {
        Map<DayOfWeek, Set<LocalTime>> weekly = new EnumMap<>(DayOfWeek.class);
        for (StepData entry : context.data().step(OnboardingSteps.EXPERIENCE_SCHEDULE).objects("weekly")) {
            DayOfWeek day = dayOfWeek(entry.text("dayOfWeek"));
            if (day == null) {
                continue;
            }
            Set<LocalTime> times = weekly.computeIfAbsent(day, key -> new TreeSet<>());
            for (String raw : entry.strings("startTimes")) {
                LocalTime time = parseTime(raw);
                if (time != null) {
                    times.add(time);
                }
            }
        }
        return SlotSchedulePlanner.atTimes(weekly, duration, LocalDate.now(), slotHorizonDays());
    }



    // --- shared reading -----------------------------------------------------------------

    private int totalDurationMinutes(Context context) {
        int total = 0;
        for (StepData activity : context.data().step(OnboardingSteps.EXPERIENCE_ITINERARY).objects("activities")) {
            total += Math.max(0, activity.integer("durationMinutes", 0));
        }
        return total > 0 ? total : DEFAULT_DURATION_MINUTES;
    }

    private BigDecimal pricePerGuest(Context context) {
        return context.data().step(OnboardingSteps.EXPERIENCE_PRICING).requiredDecimal("pricePerGuest");
    }

    private String currency(Context context) {
        return context.data().step(OnboardingSteps.EXPERIENCE_PRICING).text("currency", context.defaultCurrency());
    }

    private String timezone(Context context) {
        return context.data().step(OnboardingSteps.EXPERIENCE_SCHEDULE)
                .text("timezone", context.organizationTimezone());
    }

    private MarketplaceConfirmationType confirmationType(Context context) {
        return enumOrDefault(MarketplaceConfirmationType.class,
                context.data().step(OnboardingSteps.EXPERIENCE_BOOKING_SETTINGS).text("confirmation"),
                MarketplaceConfirmationType.INSTANT);
    }

    private ActivityDepartureType departureType(Context context) {
        return enumOrDefault(ActivityDepartureType.class,
                context.data().step(OnboardingSteps.EXPERIENCE_LOCATION).text("departureType"),
                ActivityDepartureType.MEET_UP);
    }

    private static String coverPhoto(StepData photos) {
        List<String> urls = photos.strings("photos");
        return urls.isEmpty() ? null : urls.get(0);
    }
}
