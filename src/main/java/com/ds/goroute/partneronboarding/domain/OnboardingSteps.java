package com.ds.goroute.partneronboarding.domain;

import java.util.Set;

/**
 * The step codes the wizard writes and the materializers read.
 *
 * <p>The clients own the order, the wording and the validation of a step; what has to match
 * between the three codebases is only the key a step's answers are filed under. Keeping the
 * keys here makes that contract greppable, and makes a typo a compile error on the one side
 * that can have one.
 */
public final class OnboardingSteps {

    // --- shared by every branch ---------------------------------------------------------

    /** Creates or attaches the business. Special-cased by the service: it has a side effect. */
    public static final String ORGANIZATION = "organization";
    /** Legal documents. Its state is read from the organization, not from the draft. */
    public static final String VERIFICATION = "verification";
    /** Terms acceptance on the final screen. */
    public static final String REVIEW = "review";

    // --- stay ---------------------------------------------------------------------------

    public static final String STAY_PROPERTY_TYPE = "stay.property_type";
    public static final String STAY_LOCATION = "stay.location";
    public static final String STAY_SPACE_TYPE = "stay.space_type";
    public static final String STAY_BASICS = "stay.basics";
    public static final String STAY_BATHROOMS = "stay.bathrooms";
    public static final String STAY_WHO_ELSE = "stay.who_else";
    public static final String STAY_AMENITIES = "stay.amenities";
    public static final String STAY_PHOTOS = "stay.photos";
    public static final String STAY_TITLE = "stay.title";
    public static final String STAY_DESCRIPTION = "stay.description";
    public static final String STAY_BOOKING_SETTINGS = "stay.booking_settings";
    public static final String STAY_PRICING = "stay.pricing";
    public static final String STAY_DISCOUNTS = "stay.discounts";
    public static final String STAY_POLICIES = "stay.policies";

    // --- experience ---------------------------------------------------------------------

    public static final String EXPERIENCE_CATEGORY = "exp.category";
    public static final String EXPERIENCE_CITY = "exp.city";
    public static final String EXPERIENCE_SUBTYPE = "exp.subtype";
    public static final String EXPERIENCE_LOCATION = "exp.location";
    public static final String EXPERIENCE_PHOTOS = "exp.photos";
    public static final String EXPERIENCE_ITINERARY = "exp.itinerary";
    public static final String EXPERIENCE_CAPACITY = "exp.capacity";
    public static final String EXPERIENCE_PRICING = "exp.pricing";
    public static final String EXPERIENCE_INCLUDED = "exp.included";
    public static final String EXPERIENCE_SCHEDULE = "exp.schedule";
    public static final String EXPERIENCE_TITLE_DESCRIPTION = "exp.title_description";
    public static final String EXPERIENCE_BOOKING_SETTINGS = "exp.booking_settings";
    public static final String EXPERIENCE_POLICIES = "exp.policies";

    // --- service ------------------------------------------------------------------------

    public static final String SERVICE_CATEGORY = "svc.category";
    public static final String SERVICE_CITY = "svc.city";
    public static final String SERVICE_LOCATION_MODE = "svc.location_mode";
    public static final String SERVICE_OFFERINGS = "svc.offerings";
    public static final String SERVICE_BUSINESS_HOURS = "svc.business_hours";
    public static final String SERVICE_TITLE_DESCRIPTION = "svc.title_description";
    public static final String SERVICE_POLICIES = "svc.policies";

    private static final Set<String> KNOWN = Set.of(
            ORGANIZATION, VERIFICATION, REVIEW,
            STAY_PROPERTY_TYPE, STAY_LOCATION, STAY_SPACE_TYPE, STAY_BASICS, STAY_BATHROOMS,
            STAY_WHO_ELSE, STAY_AMENITIES, STAY_PHOTOS, STAY_TITLE, STAY_DESCRIPTION,
            STAY_BOOKING_SETTINGS, STAY_PRICING, STAY_DISCOUNTS, STAY_POLICIES,
            EXPERIENCE_CATEGORY, EXPERIENCE_CITY, EXPERIENCE_SUBTYPE, EXPERIENCE_LOCATION,
            EXPERIENCE_PHOTOS, EXPERIENCE_ITINERARY, EXPERIENCE_CAPACITY, EXPERIENCE_PRICING,
            EXPERIENCE_INCLUDED, EXPERIENCE_SCHEDULE, EXPERIENCE_TITLE_DESCRIPTION,
            EXPERIENCE_BOOKING_SETTINGS, EXPERIENCE_POLICIES,
            SERVICE_CATEGORY, SERVICE_CITY, SERVICE_LOCATION_MODE, SERVICE_OFFERINGS,
            SERVICE_BUSINESS_HOURS, SERVICE_TITLE_DESCRIPTION, SERVICE_POLICIES);

    private OnboardingSteps() {
    }

    /**
     * Rejects a step code nothing will ever read. Storing one would look like it worked and
     * then vanish at submit, which is the most expensive kind of silent failure in a wizard.
     */
    public static boolean isKnown(String stepCode) {
        return stepCode != null && KNOWN.contains(stepCode);
    }
}
