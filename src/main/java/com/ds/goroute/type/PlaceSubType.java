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
