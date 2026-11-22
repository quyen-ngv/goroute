package com.ds.goroute.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The terms a place is found by through where it is: its street, ward, old district, province
 * (epic 06 §2.3). A Vietnamese syllable alone says nothing ("nhà", "nam", "hà" are in thousands
 * of addresses), so an address matches by <em>pairs of neighbouring syllables</em>
 * ("nha_chung", "cua_nam", "ha_noi"), and by a lone word only when a part of the address is
 * that one word ("Huế", "Fitzroy").
 *
 * <p>The words that only say what kind of unit follows ("Đường", "P.", "Phường", "TP.") come
 * and go in both the stored addresses and what people type, so a part is indexed without the
 * ones it starts with, and {@link #markers} tells a query which of its words may be one. Kept
 * with the name, "P. Lý Quốc Sư" would give the pair "pho_ly" and answer "phở lý quốc sư" with
 * every place of that street.
 */
public final class PlaceAddressTerms {

    /** What {@link #of} reads at most from one address; the rest of a very long one is left out. */
    private static final int MAX_TOKENS_PER_PART = 24;
    private static final int MAX_PARTS = 12;
    /** A number this long inside an address is a postal code, not part of a name. */
    private static final int POSTAL_CODE_LENGTH = 4;

    /** Google's plus codes ("WCQV+9W2"), which stand in for a house number and are never typed. */
    private static final Pattern PLUS_CODE = Pattern.compile("[A-Z0-9]{4,8}\\+[A-Z0-9]{2,3}");
    /** A part of an address ends at a comma, or at a dash standing between two names. */
    private static final Pattern PART_SEPARATOR = Pattern.compile("\\s*,\\s*|\\s+[-–—|]\\s+");

    /**
     * Unit words by their folded form, with the accented spellings that mean the unit. A word
     * typed with other accents ("phở", "quán") is not one; typed without accents it may be.
     */
    private static final Map<String, Set<String>> UNIT_WORDS = Map.ofEntries(
            Map.entry("duong", Set.of("đường")),
            Map.entry("pho", Set.of("phố")),
            Map.entry("ngo", Set.of("ngõ")),
            Map.entry("ngach", Set.of("ngách")),
            Map.entry("hem", Set.of("hẻm")),
            Map.entry("phuong", Set.of("phường")),
            Map.entry("xa", Set.of("xã")),
            Map.entry("quan", Set.of("quận")),
            Map.entry("huyen", Set.of("huyện")),
            Map.entry("tinh", Set.of("tỉnh")),
            Map.entry("so", Set.of("số")),
            Map.entry("tp", Set.of()),
            Map.entry("tt", Set.of()),
            Map.entry("tx", Set.of()),
            Map.entry("kp", Set.of()));
    /** Unit names of two words, folded: both words are the unit. */
    private static final Set<String> TWO_WORD_UNITS = Set.of("thanh pho", "thi tran", "thi xa", "khu pho");
    /** How stored addresses shorten a unit word; "p" is a ward before a number and a street otherwise. */
    private static final Map<String, String> ABBREVIATIONS = Map.of(
            "d", "duong", "ng", "ngo", "ngh", "ngach", "h", "hem", "q", "quan", "x", "xa");

    /**
     * Other names people type for a province, by its folded name. Only spellings of the province
     * itself: the towns inside it ("đà lạt" for Lâm Đồng) would match every place of the province.
     */
    private static final Map<String, List<String>> PROVINCE_SHORTCUTS = Map.of(
            "ho chi minh", List.of("Sài Gòn", "hcm", "tphcm", "sg", "hochiminh"),
            "ha noi", List.of("hn"));

    /**
     * @param pairs        neighbouring words of each part, joined by {@link LuceneKeywordQuery#PAIR_SEPARATOR}
     * @param singles      parts that are one word, and each pair written as one word ("hanoi")
     * @param houseNumbers the numbers a part starts with
     */
    public record Terms(Set<String> pairs, Set<String> singles, Set<String> houseNumbers) {
        public static final Terms EMPTY = new Terms(Set.of(), Set.of(), Set.of());
    }

    private PlaceAddressTerms() {
    }

    /**
     * The terms of an address and of the names of the areas the place lies in (its ward, its
     * province), which the address itself often leaves out or spells the old way.
     */
    public static Terms of(String address, Collection<String> areaNames) {
        Set<String> pairs = new LinkedHashSet<>();
        Set<String> singles = new LinkedHashSet<>();
        Set<String> houseNumbers = new LinkedHashSet<>();
        if (address != null && !address.isBlank()) {
            String[] parts = PART_SEPARATOR.split(PLUS_CODE.matcher(address).replaceAll(" "));
            for (int index = 0; index < parts.length && index < MAX_PARTS; index++) {
                addPart(parts[index], true, pairs, singles, houseNumbers);
            }
        }
        if (areaNames != null) {
            for (String name : areaNames) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                addPart(name, false, pairs, singles, houseNumbers);
                String folded = String.join(" ", folded(name));
                for (String shortcut : PROVINCE_SHORTCUTS.getOrDefault(folded, List.of())) {
                    addPart(shortcut, false, pairs, singles, houseNumbers);
                }
            }
        }
        return new Terms(pairs, singles, houseNumbers);
    }

    /**
     * Which words of a keyword may be unit words: "phố" in "phở mai phố nhà chung", "phường" in
     * "phường cửa nam", both words of "thành phố". A unit word before a number ("quận 1") or at
     * the end is not one: it is part of the name.
     */
    public static boolean[] markers(List<LuceneKeywordQuery.Word> words) {
        boolean[] markers = new boolean[words.size()];
        for (int index = 0; index + 1 < words.size(); index++) {
            LuceneKeywordQuery.Word word = words.get(index);
            if (startsWithDigit(words.get(index + 1).folded())) {
                continue;
            }
            if (index > 0 && TWO_WORD_UNITS.contains(words.get(index - 1).folded() + " " + word.folded())) {
                markers[index - 1] = true;
                markers[index] = true;
                continue;
            }
            Set<String> accented = UNIT_WORDS.get(word.folded());
            if (accented != null && (word.accented() == null || accented.contains(word.accented()))) {
                markers[index] = true;
            }
        }
        return markers;
    }

    private static void addPart(String part, boolean readHouseNumber, Set<String> pairs, Set<String> singles,
                                Set<String> houseNumbers) {
        List<String> tokens = folded(part);
        if (tokens.isEmpty()) {
            return;
        }
        int start = 0;
        if (readHouseNumber) {
            // "Số 162 Đường Thánh Mẫu", "3/10-3/12 Đường Hai Bà Trưng": the leading numbers,
            // when a name follows them.
            int first = tokens.size() > 1 && "so".equals(tokens.get(0)) && startsWithDigit(tokens.get(1)) ? 1 : 0;
            int end = first;
            while (end < tokens.size() && startsWithDigit(tokens.get(end))) {
                end++;
            }
            if (end > first && end < tokens.size()) {
                houseNumbers.addAll(tokens.subList(first, end));
                start = end;
            }
        }
        List<String> named = new ArrayList<>();
        for (int index = start; index < tokens.size(); index++) {
            String token = tokens.get(index);
            boolean postalCode = index > start && token.length() >= POSTAL_CODE_LENGTH && allDigits(token);
            if (!postalCode) {
                named.add(token);
            }
        }
        addTerms(withoutLeadingUnits(spellOutAbbreviations(named)), pairs, singles);
    }

    /** "P. Nhà Chung" → "pho nha chung", "P. 5" → "phuong 5", "Đ. Trần Phú" → "duong tran phu". */
    private static List<String> spellOutAbbreviations(List<String> tokens) {
        if (tokens.size() < 2) {
            return tokens;
        }
        String first = tokens.get(0);
        String spelled = "p".equals(first)
                ? (startsWithDigit(tokens.get(1)) ? "phuong" : "pho")
                : ABBREVIATIONS.get(first);
        if (spelled == null) {
            return tokens;
        }
        List<String> result = new ArrayList<>(tokens);
        result.set(0, spelled);
        return result;
    }

    /** The part without the unit words it starts with, unless a number follows ("quận 1"). */
    private static List<String> withoutLeadingUnits(List<String> tokens) {
        int start = 0;
        while (start + 1 < tokens.size() && !startsWithDigit(tokens.get(start + 1))) {
            if (start + 2 < tokens.size() && !startsWithDigit(tokens.get(start + 2))
                    && TWO_WORD_UNITS.contains(tokens.get(start) + " " + tokens.get(start + 1))) {
                start += 2;
            } else if (UNIT_WORDS.containsKey(tokens.get(start))) {
                start++;
            } else {
                break;
            }
        }
        return start == 0 ? tokens : tokens.subList(start, tokens.size());
    }

    private static void addTerms(List<String> tokens, Set<String> pairs, Set<String> singles) {
        if (tokens.size() == 1) {
            singles.add(tokens.get(0));
            return;
        }
        for (int index = 0; index + 1 < tokens.size(); index++) {
            String first = tokens.get(index);
            String second = tokens.get(index + 1);
            pairs.add(first + LuceneKeywordQuery.PAIR_SEPARATOR + second);
            if (!startsWithDigit(first) && !startsWithDigit(second)) {
                singles.add(first + second);
            }
        }
    }

    private static List<String> folded(String text) {
        return LuceneKeywordQuery.tokens(text, MAX_TOKENS_PER_PART).stream()
                .map(LuceneKeywordQuery.Word::folded)
                .toList();
    }

    private static boolean startsWithDigit(String token) {
        return !token.isEmpty() && Character.isDigit(token.charAt(0));
    }

    private static boolean allDigits(String token) {
        return token.chars().allMatch(Character::isDigit);
    }
}
