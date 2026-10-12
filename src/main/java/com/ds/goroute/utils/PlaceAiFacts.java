package com.ds.goroute.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What an LLM needs to know about a place beyond its name, in as few tokens as possible.
 *
 * <p>Shared by the AI trip planner and the chat assistant so both read the same facts the same
 * way. Every method is total: malformed or missing JSON gives an empty answer, never an error,
 * because one bad row must not cost a whole itinerary.
 */
public final class PlaceAiFacts {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d.,]*");
    private static final Pattern PHOTO_TITLE = Pattern.compile("(?i)photo \\d+ of \\d+");

    /**
     * Google "about" option names (lower case) mapped to the short tags the prompts describe.
     * A whitelist on purpose: the rest (parking, restroom, payment methods…) never changes a plan.
     */
    private static final Map<String, String> ABOUT_TAGS = Map.ofEntries(
            Map.entry("breakfast", "breakfast"),
            Map.entry("brunch", "brunch"),
            Map.entry("lunch", "lunch"),
            Map.entry("dinner", "dinner"),
            Map.entry("dessert", "dessert"),
            Map.entry("late-night food", "late_night"),
            Map.entry("good for kids", "kids"),
            Map.entry("kids' menu", "kids"),
            Map.entry("kids’ menu", "kids"),
            Map.entry("high chairs", "kids"),
            Map.entry("good for kids birthday", "kids"),
            Map.entry("vegetarian options", "veg"),
            Map.entry("vegan options", "vegan"),
            Map.entry("halal food", "halal"),
            Map.entry("alcohol", "alcohol"),
            Map.entry("beer", "alcohol"),
            Map.entry("wine", "alcohol"),
            Map.entry("cocktails", "alcohol"),
            Map.entry("hard liquor", "alcohol"),
            Map.entry("romantic", "romantic"),
            Map.entry("family-friendly", "family"),
            Map.entry("family friendly", "family"),
            Map.entry("groups", "groups"),
            Map.entry("solo dining", "solo"),
            Map.entry("great view", "view"),
            Map.entry("great views", "view"),
            Map.entry("rooftop seating", "rooftop"),
            Map.entry("live music", "live_music"),
            Map.entry("live performances", "live_music"),
            Map.entry("outdoor seating", "outdoor"),
            Map.entry("reservations required", "reservation_required"),
            Map.entry("dinner reservations recommended", "reservation_recommended"),
            Map.entry("lunch reservations recommended", "reservation_recommended"),
            Map.entry("tourists", "touristy"));

    private PlaceAiFacts() {
    }

    /**
     * A compact price label: {@code "1-100k₫"} from Google's {@code "₫1–100,000"}, the AI-enriched
     * {@code price_level} ({@code "affordable"}) when there is no range, otherwise null.
     */
    public static String priceLabel(String priceRange, String attributesJson) {
        String fromRange = compactRange(priceRange);
        if (fromRange != null) return fromRange;
        JsonNode level = read(attributesJson).path("price_level").path("value");
        if (level.isTextual() && !level.asText().isBlank()) {
            return level.asText().trim().toLowerCase(Locale.ROOT);
        }
        return null;
    }

    static String compactRange(String priceRange) {
        if (priceRange == null || priceRange.isBlank()) return null;
        String raw = priceRange.trim();
        String currency = raw.contains("₫") || raw.toUpperCase(Locale.ROOT).contains("VND") ? "₫"
                : raw.contains("$") ? "$" : raw.contains("€") ? "€" : "";
        List<String> numbers = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(raw);
        while (matcher.find() && numbers.size() < 2) numbers.add(shortNumber(matcher.group()));
        if (numbers.isEmpty()) {
            // "₫₫" / "$$": the level symbols are already short.
            return raw.length() <= 8 ? raw : null;
        }
        String body = String.join("-", numbers);
        if (raw.contains("+") && numbers.size() == 1) body = body + "+";
        return currency.equals("₫") ? body + currency : currency + body;
    }

    private static String shortNumber(String digits) {
        String clean = digits.replace(",", "").replace(".", "");
        try {
            long value = Long.parseLong(clean);
            if (value >= 1_000_000 && value % 100_000 == 0) {
                long tenths = value / 100_000;
                return tenths % 10 == 0 ? (tenths / 10) + "M" : (tenths / 10) + "." + (tenths % 10) + "M";
            }
            if (value >= 1_000 && value % 1_000 == 0) return (value / 1_000) + "k";
            return String.valueOf(value);
        } catch (NumberFormatException e) {
            return digits;
        }
    }

    /**
     * Whitelisted, de-duplicated tags from Google's "about" sections ({@code [{id, name,
     * options:[{name, enabled}]}]}), in a stable order. Accessibility options all become
     * {@code wheelchair}.
     */
    public static List<String> aboutTags(String aboutJson) {
        JsonNode root = read(aboutJson);
        if (!root.isArray()) return List.of();
        Set<String> tags = new LinkedHashSet<>();
        for (JsonNode section : root) {
            for (JsonNode option : section.path("options")) {
                if (option.has("enabled") && !option.path("enabled").asBoolean(false)) continue;
                String name = option.path("name").asText("").trim().toLowerCase(Locale.ROOT);
                if (name.isEmpty()) continue;
                if (name.contains("wheelchair")) {
                    tags.add("wheelchair");
                    continue;
                }
                String tag = ABOUT_TAGS.get(name);
                if (tag != null) tags.add(tag);
            }
        }
        return List.copyOf(tags);
    }

    /** Dish names from the scraped menu ({@code highlights[].title}, {@code data[].title}), at most {@code limit}. */
    public static List<String> menuHighlights(String menuJson, int limit) {
        List<String> out = new ArrayList<>();
        collectTitles(read(menuJson), out);
        return out.stream()
                .map(String::trim)
                .filter(title -> !title.isEmpty() && !PHOTO_TITLE.matcher(title).matches())
                .distinct()
                .limit(Math.max(0, limit))
                .toList();
    }

    private static void collectTitles(JsonNode node, List<String> out) {
        if (node.isObject()) {
            JsonNode highlight = node.get("highlightTitle");
            JsonNode title = node.get("title");
            if (highlight != null && highlight.isTextual()) out.add(highlight.asText());
            else if (title != null && title.isTextual()) out.add(title.asText());
            node.forEach(child -> collectTitles(child, out));
        } else if (node.isArray()) {
            node.forEach(child -> collectTitles(child, out));
        }
    }

    private static JsonNode read(String json) {
        if (json == null || json.isBlank()) return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        try {
            JsonNode node = JSON.readTree(json);
            return node == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : node;
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
        }
    }
}
