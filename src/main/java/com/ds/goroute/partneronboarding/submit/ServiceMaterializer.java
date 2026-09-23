package com.ds.goroute.partneronboarding.submit;

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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A service: one listing, one package per offering, bookable inside the business's opening
 * hours.
 *
 * <p>An offering ("60-minute deep tissue") is a package because that is what a guest picks
 * and pays for. The opening hours are not stored as a rule — the marketplace sells slots, so
 * the hours are divided by each offering's own length and written as real bookable times.
 * That is why two offerings of different lengths produce two different grids from the same
 * opening hours, which is exactly what a spa means by its schedule.
 */
@Component
public class ServiceMaterializer extends ActivityMaterializerSupport {

    private static final int DEFAULT_DURATION_MINUTES = 60;
    private static final int DEFAULT_MAX_GUESTS = 1;

    public ServiceMaterializer(ActivityCommerceService activityService, BusinessConfigService businessConfig) {
        super(activityService, businessConfig);
    }

    @Override
    public ListingKind kind() {
        return ListingKind.SERVICE;
    }

    @Override
    protected UpsertMarketplaceActivityRequest product(Context context) {
        StepData titleStep = context.data().step(OnboardingSteps.SERVICE_TITLE_DESCRIPTION);
        StepData locationMode = context.data().step(OnboardingSteps.SERVICE_LOCATION_MODE);
        StepData meetingPlace = locationMode.object("meetingPlace");

        UpsertMarketplaceActivityRequest request = new UpsertMarketplaceActivityRequest();
        request.setOrganizationId(context.organizationId());
        request.setActivityType(ActivityProductType.OTHER);
        request.setTitle(titleStep.requiredText("title"));
        request.setDescription(titleStep.text("description"));
        applyCity(request, context.data().step(OnboardingSteps.SERVICE_CITY));
        applyLocation(request, locationMode, meetingPlace);
        request.setPriceCurrency(currency(context));
        request.setPriceAmount(cheapestOffering(context));
        request.setCancellationPolicy(cancellationPolicy(context.data().step(OnboardingSteps.SERVICE_POLICIES)));
        request.setProductStatus(MarketplacePublicationStatus.DRAFT);
        return request;
    }

    /**
     * "You travel to guests" and "guests come to you" are not exclusive, and only the second
     * has an address to show. The travelling radius has no field of its own, so it is stated
     * where a guest reads practical conditions rather than dropped.
     */
    private void applyLocation(UpsertMarketplaceActivityRequest request, StepData locationMode, StepData meetingPlace) {
        String address = meetingPlace.text("address");
        if (address != null) {
            request.setMeetingPoint(address);
            request.setActivityAddress(address);
            BigDecimal latitude = meetingPlace.decimal("latitude");
            BigDecimal longitude = meetingPlace.decimal("longitude");
            if (latitude != null && longitude != null) {
                GeoCoordinateDto coordinate = new GeoCoordinateDto();
                coordinate.setLat(latitude);
                coordinate.setLng(longitude);
                request.setDestinationCoordinates(List.of(coordinate));
            }
        }
        List<String> notes = locationMode.strings("goodToKnowLines");
        if (!notes.isEmpty()) {
            request.setGoodToKnow(notes);
        }
    }

    @Override
    protected List<PackagePlan> packages(Context context) {
        List<StepData> offerings = context.data().step(OnboardingSteps.SERVICE_OFFERINGS).objects("offerings");
        List<SlotSchedulePlanner.HoursRange> hours = businessHours(context);
        String timezone = timezone(context);
        LocalDate today = LocalDate.now();
        int horizon = slotHorizonDays();

        List<PackagePlan> plans = new ArrayList<>(offerings.size());
        int index = 0;
        for (StepData offering : offerings) {
            String name = offering.text("title");
            if (name == null) {
                continue;
            }
            index++;
            int duration = Math.max(1, offering.integer("durationMinutes", DEFAULT_DURATION_MINUTES));
            int capacity = Math.max(1, offering.integer("maxGuests", DEFAULT_MAX_GUESTS));
            plans.add(new PackagePlan(
                    offeringPackage(context, offering, "SVC-" + index, name, duration, capacity),
                    SlotSchedulePlanner.withinHours(hours, duration, today, horizon),
                    capacity,
                    timezone));
        }
        return plans;
    }

    private UpsertActivityPackageRequest offeringPackage(Context context, StepData offering, String code,
                                                         String name, int duration, int capacity) {
        BigDecimal price = offering.requiredDecimal("price");
        boolean perGuest = !"PER_GROUP".equalsIgnoreCase(offering.text("priceUnit", "PER_GUEST"));

        UpsertActivityPackageRequest request = new UpsertActivityPackageRequest();
        request.setCode(code);
        request.setName(name);
        request.setDescription(offering.text("description"));
        request.setCurrency(currency(context));
        request.setBasePrice(price);
        request.setMinQuantity(1);
        request.setMaxQuantity(capacity);
        // Per guest means the counter multiplies the price; per group means one line whatever
        // the party size, which the marketplace expresses as a package with no units.
        request.setUnits(perGuest ? List.of(guestUnit(price, capacity)) : List.of());
        request.setConfirmationType(MarketplaceConfirmationType.INSTANT);
        request.setCancellationPolicy(cancellationPolicy(context.data().step(OnboardingSteps.SERVICE_POLICIES)));
        request.setDetails(details(context, duration));
        request.setAttributes(attributes(context, offering, perGuest));
        request.setStatus(MarketplaceAvailabilityStatus.ENABLED);
        return request;
    }

    private ActivityPackageUnit guestUnit(BigDecimal price, int capacity) {
        ActivityPackageUnit unit = new ActivityPackageUnit();
        unit.setCode("GUEST");
        unit.setName("Guest");
        unit.setUnitType(ActivityUnitType.ADULT);
        unit.setPrice(price);
        unit.setPaxCount(1);
        unit.setMaxQuantity(capacity);
        return unit;
    }

    private ActivityPackageDetails details(Context context, int duration) {
        ActivityPackageDetails details = new ActivityPackageDetails();
        details.setDurationMinutes(duration);
        details.setAreaName(context.data().step(OnboardingSteps.SERVICE_CITY).text("name"));
        return details;
    }

    /**
     * What the marketplace has no column for but the listing is meaningless without: the kind
     * of treatment, how the price is counted, the minimum a booking is worth, where the
     * provider is willing to travel, and the hours behind the generated slots.
     */
    private Map<String, Object> attributes(Context context, StepData offering, boolean perGuest) {
        StepData locationMode = context.data().step(OnboardingSteps.SERVICE_LOCATION_MODE);
        Map<String, Object> attributes = new LinkedHashMap<>();
        putIfPresent(attributes, "category", context.data().step(OnboardingSteps.SERVICE_CATEGORY).text("category"));
        putIfPresent(attributes, "serviceType", offering.text("serviceType"));
        attributes.put("priceUnit", perGuest ? "PER_GUEST" : "PER_GROUP");
        putIfPresent(attributes, "minimumPricePerBooking", offering.decimal("minPricePerBooking"));
        List<String> modes = locationMode.strings("modes");
        if (!modes.isEmpty()) {
            attributes.put("serviceModes", modes);
        }
        StepData area = locationMode.object("serviceArea");
        if (area.has("maxDriveMinutes")) {
            Map<String, Object> serviceArea = new LinkedHashMap<>();
            putIfPresent(serviceArea, "startAddress", area.text("startAddress"));
            putIfPresent(serviceArea, "latitude", area.decimal("latitude"));
            putIfPresent(serviceArea, "longitude", area.decimal("longitude"));
            putIfPresent(serviceArea, "maxDriveMinutes", area.integer("maxDriveMinutes"));
            attributes.put("serviceArea", serviceArea);
        }
        attributes.put("businessHours", rawBusinessHours(context));
        return attributes;
    }

    // --- opening hours -------------------------------------------------------------------

    private List<SlotSchedulePlanner.HoursRange> businessHours(Context context) {
        List<SlotSchedulePlanner.HoursRange> ranges = new ArrayList<>();
        for (StepData range : context.data().step(OnboardingSteps.SERVICE_BUSINESS_HOURS).objects("ranges")) {
            Set<DayOfWeek> days = new TreeSet<>();
            for (String raw : range.strings("days")) {
                DayOfWeek day = dayOfWeek(raw);
                if (day != null) {
                    days.add(day);
                }
            }
            LocalTime from = parseTime(range.text("from"));
            LocalTime to = parseTime(range.text("to"));
            SlotSchedulePlanner.HoursRange parsed = new SlotSchedulePlanner.HoursRange(days, from, to);
            if (parsed.isUsable()) {
                ranges.add(parsed);
            }
        }
        return ranges;
    }

    /** Kept verbatim on the package so a later screen can show the hours, not just the slots. */
    private List<Map<String, Object>> rawBusinessHours(Context context) {
        List<Map<String, Object>> raw = new ArrayList<>();
        for (StepData range : context.data().step(OnboardingSteps.SERVICE_BUSINESS_HOURS).objects("ranges")) {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("days", range.strings("days"));
            putIfPresent(value, "from", range.text("from"));
            putIfPresent(value, "to", range.text("to"));
            raw.add(value);
        }
        return raw;
    }

    // --- shared reading -------------------------------------------------------------------

    private BigDecimal cheapestOffering(Context context) {
        BigDecimal cheapest = null;
        for (StepData offering : context.data().step(OnboardingSteps.SERVICE_OFFERINGS).objects("offerings")) {
            BigDecimal price = offering.decimal("price");
            if (price != null && (cheapest == null || price.compareTo(cheapest) < 0)) {
                cheapest = price;
            }
        }
        return cheapest;
    }

    private String currency(Context context) {
        return context.data().step(OnboardingSteps.SERVICE_OFFERINGS)
                .text("currency", context.defaultCurrency());
    }

    private String timezone(Context context) {
        return context.data().step(OnboardingSteps.SERVICE_BUSINESS_HOURS)
                .text("timezone", context.organizationTimezone());
    }


}
