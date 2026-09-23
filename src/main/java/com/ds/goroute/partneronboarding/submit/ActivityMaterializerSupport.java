package com.ds.goroute.partneronboarding.submit;

import com.ds.goroute.dto.request.BulkCreateActivitySlotsRequest;
import com.ds.goroute.dto.request.UpsertActivitySlotRequest;
import com.ds.goroute.dto.request.UpsertMarketplaceActivityRequest;
import com.ds.goroute.dto.response.ActivityPackageResponse;
import com.ds.goroute.dto.response.MarketplaceActivityResponse;
import com.ds.goroute.partneronboarding.domain.StepData;
import com.ds.goroute.partneronboarding.dto.SubmitResultResponse;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MarketplaceSlotStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shape both non-stay branches share: one product, one or more packages, and the
 * departures those packages run.
 *
 * <p>An experience and a service differ entirely in what they ask a partner and not at all in
 * what they write, so the writing lives here once and the two subclasses only describe the
 * product and its packages. That also means a slot is created the same way in both — through
 * the bulk endpoint, inside the caller's transaction — rather than in two loops that will
 * eventually disagree about cutoffs or capacity.
 */
public abstract class ActivityMaterializerSupport implements ListingMaterializer {

    /** One package and the departures it should open with. */
    protected record PackagePlan(com.ds.goroute.dto.request.UpsertActivityPackageRequest request,
                                 List<SlotSchedulePlanner.PlannedSlot> slots,
                                 int capacity,
                                 String timezone) {
    }

    private final ActivityCommerceService activityService;
    private final BusinessConfigService businessConfig;

    protected ActivityMaterializerSupport(ActivityCommerceService activityService,
                                          BusinessConfigService businessConfig) {
        this.activityService = activityService;
        this.businessConfig = businessConfig;
    }

    /** The listing itself: what it is, where it happens, what guests see. */
    protected abstract UpsertMarketplaceActivityRequest product(Context context);

    /** What can be bought, and when it runs. At least one, or there is nothing to sell. */
    protected abstract List<PackagePlan> packages(Context context);

    @Override
    public final SubmitResultResponse materialize(Context context) {
        MarketplaceActivityResponse product = activityService.partnerCreate(context.actorUserId(), product(context));
        for (PackagePlan plan : packages(context)) {
            ActivityPackageResponse created =
                    activityService.partnerCreatePackage(context.actorUserId(), product.getId(), plan.request());
            createSlots(context, created, plan);
        }
        return SubmitResultResponse.builder()
                .draftId(context.draftId())
                .listingKind(kind())
                .activityId(product.getId())
                .build();
    }

    private void createSlots(Context context, ActivityPackageResponse created, PackagePlan plan) {
        if (plan.slots().isEmpty()) {
            return;
        }
        List<UpsertActivitySlotRequest> slots = new ArrayList<>(plan.slots().size());
        for (SlotSchedulePlanner.PlannedSlot planned : plan.slots()) {
            UpsertActivitySlotRequest slot = new UpsertActivitySlotRequest();
            slot.setStartsAt(planned.startsAt());
            slot.setEndsAt(planned.endsAt());
            slot.setTimezone(plan.timezone());
            slot.setCapacity(plan.capacity());
            slot.setBlockedQuantity(0);
            slot.setBookingCutoffMinutes(0);
            slot.setStatus(MarketplaceSlotStatus.ENABLED);
            slots.add(slot);
        }
        BulkCreateActivitySlotsRequest request = new BulkCreateActivitySlotsRequest();
        request.setSlots(slots);
        activityService.partnerCreateSlots(context.actorUserId(), created.getId(), request);
    }

    // --- shared reading -----------------------------------------------------------------

    protected int slotHorizonDays() {
        return businessConfig.getInt(BusinessConfigKey.PARTNER_ONBOARDING_SLOT_HORIZON_DAYS);
    }

    /**
     * Readiness requires a cancellation policy, and an unanswered policy screen must not be
     * the reason a partner cannot sell. Falls back to free cancellation a day ahead — the
     * friendliest real policy, never a silently strict one.
     */
    protected Map<String, Object> cancellationPolicy(StepData policies) {
        StepData cancellation = policies.object("cancellation");
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("type", cancellation.text("type", "FREE_CANCELLATION"));
        value.put("freeCancellationHours", cancellation.integer("freeCancellationHours", 24));
        Integer penaltyPercent = cancellation.integer("penaltyPercent");
        if (penaltyPercent != null) {
            value.put("penaltyPercent", penaltyPercent);
        }
        return value;
    }

    /** The city a partner typed, as the one destination the listing is filed under. */
    protected void applyCity(UpsertMarketplaceActivityRequest request, StepData city) {
        String name = city.text("name");
        if (name != null) {
            request.setDestinations(List.of(name));
        }
    }

    /** Weekday as both schedule screens send it ("MONDAY"); anything else reads as absent. */
    protected static java.time.DayOfWeek dayOfWeek(String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.time.DayOfWeek.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** {@code HH:mm}, tolerating the seconds a picker sometimes appends. */
    protected static java.time.LocalTime parseTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            return java.time.LocalTime.parse(value.length() > 5 ? value.substring(0, 5) : value);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    protected static <E extends Enum<E>> E enumOrDefault(Class<E> type, String value, E fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
    }

    protected static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }
}
