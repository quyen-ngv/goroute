package com.ds.goroute.service.assistant;

import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceSubType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Pengoo's place request reduced to what the catalogue search can use: words to look for in
 * place names, sub-type codes, and the place group.
 *
 * <p>Names are searched word by word, so a kind word ("cà phê", "bảo tàng") in the words would
 * rank by the word instead of the kind and miss every café not named "cà phê". Kind words
 * become sub types instead, and filler ("quán", "gần đây", "ngon") is dropped. What is left is
 * a dish, a brand or a name, and only that searches names.
 */
record PlaceQuery(String words, List<String> subTypes, List<String> groups) {

    /** Kind phrases (without accents) to the sub types they mean. Longest phrases are tried first. */
    private static final Map<String, List<String>> KIND_PHRASES = new LinkedHashMap<>();
    /** Phrases asking for somewhere to sleep: the accommodation group, which has no sub types. */
    private static final Set<String> STAY_PHRASES = Set.of(
            "khach san", "hotel", "homestay", "home stay", "resort", "nha nghi", "hostel", "villa",
            "cho o", "cho ngu", "luu tru", "accommodation", "motel", "guesthouse", "guest house", "can ho");
    /** Words that say nothing about the name of a place. */
    private static final Set<String> FILLER = Set.of(
            "quan", "nha hang", "tiem", "dia diem", "diem", "cho", "noi", "o", "gan", "gan day", "quanh", "tai",
            "ngon", "dep", "tot", "hay", "re", "binh dan", "noi tieng", "dong nguoi", "yen tinh", "chill", "view",
            "de", "gap", "gap go", "ban", "ban moi", "ket ban", "lam quen", "nguoi moi", "cho ngoi", "ngoi",
            "mot", "minh", "cac", "nhung", "nao", "va", "voi", "cua", "cho minh", "tim", "goi y",
            "place", "places", "spot", "spots", "good", "best", "nice", "near", "nearby", "cheap", "the", "a",
            "to", "meet", "new", "people", "friends", "for", "with", "and", "in");

    static {
        kind(List.of("CAFE"), "ca phe", "cafe", "coffee", "caphe", "cf");
        kind(List.of("TEA"), "tra sua", "tra chanh", "tra", "tea", "milk tea");
        kind(List.of("BAR"), "bar", "pub", "bia", "beer", "cocktail", "rooftop", "quan nhau", "nhau");
        kind(List.of("BAKERY"), "tiem banh", "bakery", "banh ngot");
        kind(List.of("DESSERT"), "che", "kem", "dessert", "ice cream", "trang mieng");
        kind(List.of("STREET_FOOD"), "via he", "an vat", "street food", "do an duong pho");
        kind(List.of("SEAFOOD"), "hai san", "seafood", "oc");
        kind(List.of("VEGETARIAN", "VEGAN"), "an chay", "chay", "vegetarian", "vegan");
        kind(List.of("HALAL"), "halal");
        kind(List.of("RESTAURANT"), "restaurant", "an toi", "an trua");
        kind(List.of("MUSEUM"), "bao tang", "museum", "trien lam", "gallery");
        kind(List.of("SPIRITUAL"), "chua", "den", "nha tho", "pagoda", "temple", "church", "mieu", "thanh duong");
        kind(List.of("HERITAGE"), "di tich", "co do", "lang", "heritage", "phu co", "pho co", "thanh co");
        kind(List.of("BEACH"), "bai bien", "bien", "beach");
        kind(List.of("PARK"), "cong vien", "park", "vuon");
        kind(List.of("NATURE"), "thac", "nui", "rung", "waterfall", "mountain", "nature", "trekking");
        kind(List.of("VIEWPOINT"), "ngam canh", "view point", "viewpoint", "hoang hon", "sunset", "ngam hoang hon");
        // A bare "cho" is also the verb "give" ("cho mình"), so only market phrases count.
        kind(List.of("MARKET"), "cho dem", "cho truyen thong", "market", "night market", "cho sang", "cho ca",
                "cho hai san", "cho bien", "cho que", "cho phien", "cho noi", "fish market", "morning market");
        kind(List.of("SHOPPING"), "mua sam", "shopping", "mall", "trung tam thuong mai", "qua luu niem");
        kind(List.of("ENTERTAINMENT"), "board game", "boardgame", "karaoke", "bowling", "giai tri", "entertainment",
                "escape room", "phong game", "billiard", "bida");
        kind(List.of("THEME_PARK"), "khu vui choi", "cong vien giai tri", "theme park", "water park");
        kind(List.of("LANDMARK"), "cau", "landmark", "bieu tuong");
    }

    private static void kind(List<String> subTypes, String... phrases) {
        for (String phrase : phrases) KIND_PHRASES.putIfAbsent(phrase, subTypes);
    }

    static PlaceQuery of(String query, List<String> typeArgs, String kindArg) {
        Set<String> subTypes = new LinkedHashSet<>();
        boolean[] stay = {false};
        if (typeArgs != null) {
            for (String code : typeArgs) {
                PlaceSubType kind = PlaceSubType.fromCode(code);
                if (kind != null) subTypes.add(kind.name());
            }
        }
        String words = null;
        if (query != null) {
            List<String> tokens = new ArrayList<>(Arrays.asList(AssistantToolService.normalize(query)
                    .replaceAll("[^a-z0-9 ]", " ").trim().split(" +")));
            List<String> original = new ArrayList<>(Arrays.asList(query.trim().toLowerCase(Locale.ROOT)
                    .replaceAll("[\\p{Punct}]", " ").trim().split("\\s+")));
            boolean typesGiven = !subTypes.isEmpty();
            boolean[] drop = new boolean[tokens.size()];
            if (tokens.size() == original.size()) {
                markPhrases(tokens, drop, subTypes, typesGiven, stay);
            }
            List<String> kept = new ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                if (!drop[i] && !tokens.get(i).isBlank()) kept.add(i < original.size() ? original.get(i) : tokens.get(i));
            }
            words = kept.isEmpty() ? null : String.join(" ", kept);
        }
        if (stay[0] || "stay".equalsIgnoreCase(kindArg)) {
            // Places to sleep carry no sub types, so the kinds are dropped rather than intersected.
            return new PlaceQuery(words, null, List.of(PlaceGroup.ACCOMMODATION.name()));
        }
        List<String> groups = groups(kindArg, subTypes);
        return new PlaceQuery(words, subTypes.isEmpty() ? null : List.copyOf(subTypes), groups);
    }

    boolean hasKinds() {
        return subTypes != null;
    }

    /**
     * Marks kind phrases and filler, longest phrases first so "cong vien giai tri" wins over
     * "cong vien". Kind phrases add their sub types unless the model already chose types.
     */
    private static void markPhrases(List<String> tokens, boolean[] drop, Set<String> subTypes, boolean typesGiven,
                                    boolean[] stay) {
        for (int size = 4; size >= 1; size--) {
            for (int start = 0; start + size <= tokens.size(); start++) {
                if (anyDropped(drop, start, size)) continue;
                String phrase = String.join(" ", tokens.subList(start, start + size));
                List<String> kinds = KIND_PHRASES.get(phrase);
                if (kinds != null) {
                    if (!typesGiven) subTypes.addAll(kinds);
                    mark(drop, start, size);
                } else if (STAY_PHRASES.contains(phrase)) {
                    stay[0] = true;
                    mark(drop, start, size);
                } else if (FILLER.contains(phrase)) {
                    mark(drop, start, size);
                }
            }
        }
    }

    private static boolean anyDropped(boolean[] drop, int start, int size) {
        for (int i = start; i < start + size; i++) if (drop[i]) return true;
        return false;
    }

    private static void mark(boolean[] drop, int start, int size) {
        for (int i = start; i < start + size; i++) drop[i] = true;
    }

    /** The place group from the explicit kind, else from the sub types when they all share one. */
    private static List<String> groups(String kind, Set<String> subTypes) {
        // The types decide when there are any: "kind food" with MARKET (a sight) used to filter the
        // market out before the search ran (prod 2026-10-25, "chợ buổi sáng ở bãi biển").
        if (kind != null && subTypes.isEmpty()) {
            switch (kind.toLowerCase(Locale.ROOT)) {
                case "food" -> {
                    return List.of(PlaceGroup.FOOD_AND_DRINK.name());
                }
                case "sight" -> {
                    return List.of(PlaceGroup.ATTRACTIONS.name());
                }
                default -> {
                }
            }
        }
        Set<PlaceSubType.Family> families = new LinkedHashSet<>();
        for (String code : subTypes) families.add(PlaceSubType.valueOf(code).getFamily());
        if (families.size() != 1) return null;
        return List.of(families.contains(PlaceSubType.Family.FOOD)
                ? PlaceGroup.FOOD_AND_DRINK.name() : PlaceGroup.ATTRACTIONS.name());
    }
}
