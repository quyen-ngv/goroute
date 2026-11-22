package com.ds.goroute.type;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Finer kinds of a place, stored as the multi-valued {@code places.sub_types}. A place may carry
 * several (a seafood restaurant that is also halal). Each kind belongs to one family, and only
 * fits the place group of that family: food kinds on {@link PlaceGroup#FOOD_AND_DRINK},
 * attraction kinds on {@link PlaceGroup#ATTRACTIONS}, nothing on {@link PlaceGroup#ACCOMMODATION}.
 */
public enum PlaceSubType {
    NATURE(Family.ATTRACTION),
    BEACH(Family.ATTRACTION),
    PARK(Family.ATTRACTION),
    VIEWPOINT(Family.ATTRACTION),
    SPIRITUAL(Family.ATTRACTION),
    MUSEUM(Family.ATTRACTION),
    HERITAGE(Family.ATTRACTION),
    LANDMARK(Family.ATTRACTION),
    SHOPPING(Family.ATTRACTION),
    MARKET(Family.ATTRACTION),
    ENTERTAINMENT(Family.ATTRACTION),
    THEME_PARK(Family.ATTRACTION),

    RESTAURANT(Family.FOOD),
    STREET_FOOD(Family.FOOD),
    CAFE(Family.FOOD),
    TEA(Family.FOOD),
    BAR(Family.FOOD),
    BAKERY(Family.FOOD),
    DESSERT(Family.FOOD),
    SEAFOOD(Family.FOOD),
    VEGETARIAN(Family.FOOD),
    VEGAN(Family.FOOD),
    HALAL(Family.FOOD);

    /**
     * Filter-only code for "none of the kinds": a place whose sub_types is empty. Never stored;
     * {@link #toStorable} drops it like any unknown code.
     */
    public static final String UNTYPED_FILTER = "OTHER";

    public enum Family { ATTRACTION, FOOD }

    private final Family family;

    PlaceSubType(Family family) {
        this.family = family;
    }

    public Family getFamily() {
        return family;
    }

    /** A place without a group yet accepts any kind; the group decides once it is set. */
    public boolean fits(PlaceGroup group) {
        if (group == null) {
            return true;
        }
        return family == Family.FOOD
                ? group == PlaceGroup.FOOD_AND_DRINK
                : group == PlaceGroup.ATTRACTIONS;
    }

    /** The code for a value, or null when it names no kind. Case and surrounding space are ignored. */
    public static PlaceSubType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return valueOf(code.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    /** Codes of a request filter; null when nothing was asked for, so the filter is skipped. */
    public static List<String> names(Collection<PlaceSubType> kinds) {
        if (kinds == null) {
            return null;
        }
        List<String> names = kinds.stream().filter(Objects::nonNull).map(Enum::name).distinct().toList();
        return names.isEmpty() ? null : names;
    }

    /**
     * Codes of a search filter, where {@link #UNTYPED_FILTER} may stand beside the kinds; null when
     * nothing was asked for. An unknown code is a bad request rather than a filter that matches
     * nothing.
     */
    public static List<String> filterCodes(Collection<String> codes) {
        if (codes == null) {
            return null;
        }
        List<String> names = codes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .map(code -> {
                    if (code.equals(UNTYPED_FILTER) || fromCode(code) != null) {
                        return code;
                    }
                    throw new IllegalArgumentException("Unknown sub type: " + code);
                })
                .distinct()
                .toList();
        return names.isEmpty() ? null : names;
    }

    /** The kinds that fit any of {@code groups}; every kind when no group is given. */
    public static List<PlaceSubType> fittingAny(Collection<PlaceGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of(values());
        }
        return EnumSet.allOf(PlaceSubType.class).stream()
                .filter(kind -> groups.stream().anyMatch(group -> group != null && kind.fits(group)))
                .toList();
    }

    /**
     * The storable form: known kinds that fit {@code group}, without duplicates, in declaration
     * order so equal sets always compare and cache the same. Unknown or misfiting codes are dropped.
     */
    public static String[] toStorable(Collection<String> codes, PlaceGroup group) {
        if (codes == null || codes.isEmpty()) {
            return new String[0];
        }
        EnumSet<PlaceSubType> kinds = EnumSet.noneOf(PlaceSubType.class);
        codes.stream()
                .map(PlaceSubType::fromCode)
                .filter(Objects::nonNull)
                .filter(kind -> kind.fits(group))
                .forEach(kinds::add);
        return kinds.stream().map(Enum::name).toArray(String[]::new);
    }
}
