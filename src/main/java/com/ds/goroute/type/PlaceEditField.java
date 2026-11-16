package com.ds.goroute.type;

/**
 * A place field a suggestion can change (plan §3.2). The name is also what
 * {@code places.locked_fields} stores for the fields an approval locks against the re-scrape.
 */
public enum PlaceEditField {
    NAME(false, false),
    ALIAS(false, false),
    DESCRIPTION(false, false),
    CATEGORY(false, false),
    ADDRESS(true, false),
    LOCATION(true, false),
    OPEN_HOURS(true, false),
    PHONE(true, false),
    WEBSITE(true, false),
    PRICE_RANGE(true, false),
    BUSINESS_STATUS(true, false),
    /** Only an operator writes attributes, from the submitter's description. */
    ATTRIBUTES(false, true);

    private final boolean locksOnApproval;
    private final boolean adminOnly;

    PlaceEditField(boolean locksOnApproval, boolean adminOnly) {
        this.locksOnApproval = locksOnApproval;
        this.adminOnly = adminOnly;
    }

    /** Whether an applied change keeps the Google re-scrape from overwriting the column. */
    public boolean locksOnApproval() {
        return locksOnApproval;
    }

    public boolean isAdminOnly() {
        return adminOnly;
    }

    /** The field for a stored or requested name; null when it names none. */
    public static PlaceEditField fromName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
