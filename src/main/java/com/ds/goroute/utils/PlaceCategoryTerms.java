package com.ds.goroute.utils;

import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSubType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The words a place is found by through what it is ("cà phê", "nhà hàng", "bảo tàng",
 * "coffee"), so "cà phê đặng thái thân" finds a café on that street whose name says neither
 * (epic 06 §2.3). Built like {@link PlaceAddressTerms}: a Vietnamese kind matches by pairs of
 * neighbouring syllables ("ca_phe"), an English one by its words.
 */
public final class PlaceCategoryTerms {

    private static final int MAX_CATEGORY_TOKENS = 8;
    /** English words too common in Google's categories to tell one kind from another. */
    private static final Set<String> IGNORED_WORDS = Set.of("and", "the", "of", "shop", "store", "place", "point");

    private static final Map<PlaceSubType, List<String>> KIND_NAMES = Map.ofEntries(
            Map.entry(PlaceSubType.NATURE, List.of("thiên nhiên", "nature")),
            Map.entry(PlaceSubType.BEACH, List.of("bãi biển", "bãi tắm", "beach")),
            Map.entry(PlaceSubType.PARK, List.of("công viên", "park")),
            Map.entry(PlaceSubType.VIEWPOINT, List.of("ngắm cảnh", "viewpoint")),
            Map.entry(PlaceSubType.SPIRITUAL, List.of("tâm linh", "temple", "pagoda", "church")),
            Map.entry(PlaceSubType.MUSEUM, List.of("bảo tàng", "museum")),
            Map.entry(PlaceSubType.HERITAGE, List.of("di tích", "di sản", "heritage")),
            Map.entry(PlaceSubType.LANDMARK, List.of("địa danh", "landmark")),
            Map.entry(PlaceSubType.SHOPPING, List.of("mua sắm", "trung tâm thương mại", "shopping", "mall")),
            Map.entry(PlaceSubType.MARKET, List.of("market")),
            Map.entry(PlaceSubType.ENTERTAINMENT, List.of("giải trí", "vui chơi", "entertainment")),
            Map.entry(PlaceSubType.THEME_PARK, List.of("khu vui chơi", "công viên giải trí", "theme park")),
            Map.entry(PlaceSubType.RESTAURANT, List.of("nhà hàng", "quán ăn", "restaurant")),
            Map.entry(PlaceSubType.STREET_FOOD, List.of("ăn vặt", "vỉa hè", "street food")),
            Map.entry(PlaceSubType.CAFE, List.of("cà phê", "quán cà phê", "cafe", "coffee", "caphe")),
            Map.entry(PlaceSubType.TEA, List.of("trà sữa", "quán trà", "tea")),
            Map.entry(PlaceSubType.BAR, List.of("quán bar", "bar", "pub")),
            Map.entry(PlaceSubType.BAKERY, List.of("tiệm bánh", "bakery")),
            Map.entry(PlaceSubType.DESSERT, List.of("tráng miệng", "dessert")),
            Map.entry(PlaceSubType.SEAFOOD, List.of("hải sản", "seafood")),
            Map.entry(PlaceSubType.VEGETARIAN, List.of("quán chay", "đồ chay", "vegetarian")),
            Map.entry(PlaceSubType.VEGAN, List.of("thuần chay", "vegan")),
            Map.entry(PlaceSubType.HALAL, List.of("halal")));

    private static final Map<PlaceGroup, List<String>> GROUP_NAMES = Map.of(
            PlaceGroup.FOOD_AND_DRINK, List.of("ăn uống", "quán ăn"),
            PlaceGroup.ATTRACTIONS, List.of("tham quan", "điểm tham quan", "attraction"),
            PlaceGroup.ACCOMMODATION, List.of("khách sạn", "nhà nghỉ", "lưu trú", "hotel", "homestay", "resort"));

    /**
     * @param pairs   neighbouring words of each kind name, joined by {@link LuceneKeywordQuery#PAIR_SEPARATOR}
     * @param singles kind names of one word, and the words of an English category
     */
    public record Terms(Set<String> pairs, Set<String> singles) {
    }

    private PlaceCategoryTerms() {
    }

    /** The terms of a place's group, its kinds and the category the scraper copied from Google. */
    public static Terms of(PlaceGroup group, String[] subTypes, String category) {
        Set<String> pairs = new LinkedHashSet<>();
        Set<String> singles = new LinkedHashSet<>();
        if (group != null) {
            GROUP_NAMES.getOrDefault(group, List.of()).forEach(name -> addName(name, pairs, singles));
        }
        if (subTypes != null) {
            for (String code : subTypes) {
                PlaceSubType kind = kindOf(code);
                if (kind != null) {
                    KIND_NAMES.getOrDefault(kind, List.of()).forEach(name -> addName(name, pairs, singles));
                }
            }
        }
        if (category != null && !category.isBlank()) {
            addName(category.replace('_', ' '), pairs, singles);
        }
        return new Terms(pairs, singles);
    }

    private static PlaceSubType kindOf(String code) {
        if (code == null) {
            return null;
        }
        try {
            return PlaceSubType.valueOf(code.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void addName(String name, Set<String> pairs, Set<String> singles) {
        List<LuceneKeywordQuery.Word> words = LuceneKeywordQuery.tokens(name, MAX_CATEGORY_TOKENS);
        if (words.isEmpty()) {
            return;
        }
        // A name with no accent in it is taken as English ("Coffee shop", "Pho restaurant"):
        // each of its words says the kind. A Vietnamese syllable alone does not.
        boolean english = words.stream().allMatch(word -> word.accented() == null);
        if (words.size() == 1 || english) {
            words.stream()
                    .map(LuceneKeywordQuery.Word::folded)
                    .filter(word -> word.length() >= 3 && !IGNORED_WORDS.contains(word))
                    .forEach(singles::add);
        }
        for (int index = 0; index + 1 < words.size(); index++) {
            pairs.add(words.get(index).folded() + LuceneKeywordQuery.PAIR_SEPARATOR + words.get(index + 1).folded());
        }
    }
}
