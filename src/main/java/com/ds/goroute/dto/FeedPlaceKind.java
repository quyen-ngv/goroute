package com.ds.goroute.dto;

import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSubType;
import lombok.Value;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * The Feed's place-kind chips: only posts about a place in one of {@code placeGroups}
 * and with one of {@code subTypes}. Either list may be null, which leaves that side
 * unfiltered.
 *
 * <p>A check-in or a review matches through its catalogue place, so one at a place
 * outside the catalogue never matches. A trip matches when any of its stops does.
 *
 * <p>A value class with getters rather than a record: the mapper XML reads it through
 * OGNL in {@code <if>} and {@code <foreach>}, and it is part of the public trip ranking
 * cache's key, so it needs equality.
 */
@Value
public class FeedPlaceKind {

    List<String> placeGroups;
    List<String> subTypes;

    /**
     * Null when the request asked for no kind, which keeps the feed unfiltered.
     *
     * @throws IllegalArgumentException for a group or sub type the catalogue does not have
     */
    public static FeedPlaceKind of(Collection<String> placeGroups, Collection<String> subTypes) {
        List<String> groups = groupNames(placeGroups);
        List<String> kinds = subTypeNames(subTypes);
        if (groups == null && kinds == null) {
            return null;
        }
        return new FeedPlaceKind(groups, kinds);
    }

    private static List<String> groupNames(Collection<String> codes) {
        List<String> names = normalized(codes).stream()
                .map(code -> {
                    PlaceGroup group = PlaceGroup.fromCode(code);
                    if (group == null) {
                        throw new IllegalArgumentException("Unknown place group: " + code);
                    }
                    return group.name();
                })
                .distinct()
                .toList();
        return names.isEmpty() ? null : names;
    }

    private static List<String> subTypeNames(Collection<String> codes) {
        List<String> names = normalized(codes).stream()
                .map(code -> {
                    PlaceSubType subType = PlaceSubType.fromCode(code);
                    if (subType == null) {
                        throw new IllegalArgumentException("Unknown sub type: " + code);
                    }
                    return subType.name();
                })
                .distinct()
                .toList();
        return names.isEmpty() ? null : names;
    }

    private static List<String> normalized(Collection<String> codes) {
        if (codes == null) {
            return List.of();
        }
        return codes.stream()
                .filter(Objects::nonNull)
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .filter(code -> !code.isEmpty())
                .toList();
    }
}
