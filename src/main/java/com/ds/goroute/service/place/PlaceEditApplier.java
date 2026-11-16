package com.ds.goroute.service.place;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.PlaceHoursDay;
import com.ds.goroute.dto.PlacePriceRange;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditGuard;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceAttributeCatalog;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceBusinessStatus;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.PlaceStatusReportReason;
import com.ds.goroute.type.PlaceSubType;
import com.ds.goroute.type.PlaceVisibilityStatus;
import com.ds.goroute.type.TranslationLocale;
import com.ds.goroute.type.TranslationSource;
import com.ds.goroute.utils.GeoDistance;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Validates, compares and writes one field of a place edit suggestion (plan §3.2).
 *
 * <p>Every value goes through {@link #normalize} first: the user's submission, an operator's
 * corrected value and an operator's own change alike, so what is stored in {@code new_value} /
 * {@code applied_value} always has the canonical §3.2 shape (OPEN_HOURS and PRICE_RANGE carry the
 * server-built {@code display}). {@link #apply} then writes it with a narrow statement of its own
 * column group, never the whole-row place update, and locks the field against the re-scrape.
 * Name and description go to the {@code MANUAL} translation row of a locale.
 */
@Component
@RequiredArgsConstructor
public class PlaceEditApplier {

    /** A pin closer than this to the stored one is not a move. */
    public static final double MIN_PIN_MOVE_METERS = 10d;
    public static final int MAX_SEARCH_ALIASES = 30;
    public static final int MAX_NAME_LENGTH = 200;
    public static final int MIN_DESCRIPTION_LENGTH = 20;
    public static final int MAX_DESCRIPTION_LENGTH = 2000;
    public static final int MAX_ADDRESS_LENGTH = 500;
    public static final int MAX_PHONE_LENGTH = 50;
    public static final int MAX_WEBSITE_LENGTH = 500;
    private static final Pattern PHONE = Pattern.compile("^[+0-9 ()\\-]{3,50}$");

    private final PlaceRepository placeRepository;
    private final PlaceTranslationService placeTranslationService;
    private final BusinessConfigService config;
    private final ObjectMapper objectMapper;

    /**
     * The canonical value of a change, or a 400 INVALID_PARAMETERS naming what is wrong.
     *
     * @param userAuthored a submission (pin-distance cap applies, ATTRIBUTES refused) rather than an operator's value
     */
    public ObjectNode normalize(PlaceEditField field, JsonNode value, PlaceEditPlaceState state, boolean userAuthored) {
        if (value == null || value.isNull() || !value.isObject()) {
            throw invalid(field + " value must be a JSON object");
        }
        try {
            return switch (field) {
                case NAME -> text("name", value, 1, MAX_NAME_LENGTH, false);
                case ALIAS -> text("alias", value, 1, MAX_NAME_LENGTH, false);
                case DESCRIPTION -> text("description", value, MIN_DESCRIPTION_LENGTH, MAX_DESCRIPTION_LENGTH, false);
                case CATEGORY -> category(value, state.place());
                case ADDRESS -> text("address", value, 1, MAX_ADDRESS_LENGTH, false);
                case LOCATION -> location(value, state.place(), userAuthored);
                case OPEN_HOURS -> openHours(value);
                case PHONE -> phone(value);
                case WEBSITE -> website(value);
                case PRICE_RANGE -> priceRange(value);
                case BUSINESS_STATUS -> businessStatus(value, state.place());
                case ATTRIBUTES -> attributes(value, userAuthored);
            };
        } catch (IllegalArgumentException exception) {
            throw invalid(field + ": " + exception.getMessage());
        }
    }

    /** What the place shows now for the field, in the same shape as {@link #normalize}'s result. */
    public ObjectNode currentValue(PlaceEditField field, PlaceEditPlaceState state, JsonNode forValue) {
        Place place = state.place();
        ObjectNode node = objectMapper.createObjectNode();
        switch (field) {
            case NAME -> node.put("name", state.displayName());
            case ALIAS -> {
                node.putNull("alias");
                ArrayNode aliases = node.putArray("aliases");
                if (place.getSearchAliases() != null) {
                    Arrays.stream(place.getSearchAliases()).forEach(aliases::add);
                }
            }
            case DESCRIPTION -> node.put("description", state.displayDescription());
            case CATEGORY -> {
                node.put("placeGroup", place.getPlaceGroup() == null ? null : place.getPlaceGroup().name());
                ArrayNode subTypes = node.putArray("subTypes");
                if (place.getSubTypes() != null) {
                    Arrays.stream(place.getSubTypes()).forEach(subTypes::add);
                }
            }
            case ADDRESS -> node.put("address", place.getAddress());
            case LOCATION -> {
                node.put("latitude", place.getLatitude());
                node.put("longitude", place.getLongitude());
            }
            case OPEN_HOURS -> {
                Map<String, List<String>> stored = readHours(place.getOpenHours());
                List<PlaceHoursDay> structured = PlaceOpenHoursFormatter.parse(stored);
                node.set("structured", structured == null ? null : objectMapper.valueToTree(structured));
                node.set("display", stored == null ? null : objectMapper.valueToTree(stored));
            }
            case PHONE -> node.put("phone", place.getPhone());
            case WEBSITE -> node.put("website", place.getWebsite());
            case PRICE_RANGE -> {
                PlacePriceRange range = PlacePriceRangeFormatter.parse(place.getPriceRange());
                node.put("min", range == null ? null : range.getMin());
                node.put("max", range == null ? null : range.getMax());
                node.put("currency", PlacePriceRangeFormatter.CURRENCY);
                node.put("display", place.getPriceRange());
            }
            case BUSINESS_STATUS -> {
                node.putNull("reason");
                node.put("businessStatus", PlaceBusinessStatus.fromStoredStatus(place.getStatus()).name());
                node.put("visibilityStatus", place.getVisibilityStatus() == null
                        ? PlaceVisibilityStatus.ACTIVE.name() : place.getVisibilityStatus().name());
            }
            case ATTRIBUTES -> {
                ObjectNode stored = storedAttributes(place);
                ObjectNode picked = node.putObject("attributes");
                JsonNode requested = forValue == null ? null : forValue.get("attributes");
                if (requested != null && requested.isObject()) {
                    requested.fieldNames().forEachRemaining(key -> picked.set(key, stored.get(key)));
                }
            }
        }
        return node;
    }

    /** Whether a normalized value would change nothing the place shows; such a change is dropped. */
    public boolean isUnchanged(PlaceEditField field, ObjectNode value, PlaceEditPlaceState state) {
        Place place = state.place();
        return switch (field) {
            case NAME -> sameText(value.path("name").asText(null), state.displayName());
            case ALIAS -> {
                String alias = value.path("alias").asText("");
                boolean known = sameTextIgnoreCase(alias, place.getTitle())
                        || sameTextIgnoreCase(alias, state.displayName());
                if (place.getSearchAliases() != null) {
                    for (String stored : place.getSearchAliases()) {
                        known |= sameTextIgnoreCase(alias, stored);
                    }
                }
                yield known;
            }
            case DESCRIPTION -> sameText(value.path("description").asText(null), state.displayDescription());
            case CATEGORY -> {
                String group = place.getPlaceGroup() == null ? null : place.getPlaceGroup().name();
                Set<String> stored = place.getSubTypes() == null ? Set.of() : Set.of(place.getSubTypes());
                Set<String> requested = new LinkedHashSet<>();
                value.path("subTypes").forEach(node -> requested.add(node.asText()));
                yield Objects.equals(group, value.path("placeGroup").asText(null)) && stored.equals(requested);
            }
            case ADDRESS -> sameText(value.path("address").asText(null), place.getAddress());
            case LOCATION -> {
                Double meters = GeoDistance.betweenOrNull(place.getLatitude(), place.getLongitude(),
                        value.path("latitude").decimalValue(), value.path("longitude").decimalValue());
                yield meters != null && meters < MIN_PIN_MOVE_METERS;
            }
            case OPEN_HOURS -> {
                Map<String, List<String>> stored = readHours(place.getOpenHours());
                yield stored != null && objectMapper.valueToTree(stored).equals(value.get("display"));
            }
            case PHONE -> sameText(nullableText(value, "phone"), place.getPhone());
            case WEBSITE -> sameText(nullableText(value, "website"), place.getWebsite());
            case PRICE_RANGE -> sameText(value.path("display").asText(null), place.getPriceRange());
            case BUSINESS_STATUS -> {
                PlaceBusinessStatus current = PlaceBusinessStatus.fromStoredStatus(place.getStatus());
                PlaceStatusReportReason reason = PlaceStatusReportReason.valueOf(value.path("reason").asText());
                yield switch (reason) {
                    case PERMANENTLY_CLOSED -> current == PlaceBusinessStatus.PERMANENTLY_CLOSED;
                    case TEMPORARILY_CLOSED -> current == PlaceBusinessStatus.TEMPORARILY_CLOSED;
                    case REOPENED -> current == PlaceBusinessStatus.OPEN;
                    case NOT_EXIST, DUPLICATE -> place.getVisibilityStatus() == PlaceVisibilityStatus.INACTIVE;
                    case MOVED -> false;
                };
            }
            case ATTRIBUTES -> false;
        };
    }

    /**
     * Writes a normalized value to the place and locks the field when it is one the re-scrape
     * would overwrite. Runs inside the review transaction; the caller reindexes after commit.
     *
     * @param locale translation locale for NAME / DESCRIPTION
     */
    public void apply(PlaceEditField field, ObjectNode value, PlaceEditPlaceState state, String locale) {
        Place place = state.place();
        UUID placeId = place.getId();
        TranslationLocale translationLocale = TranslationLocale.fromCode(locale).orElse(TranslationLocale.DEFAULT);
        switch (field) {
            case NAME -> placeTranslationService.writeName(placeId, translationLocale,
                    value.path("name").asText(), TranslationSource.MANUAL);
            case ALIAS -> {
                List<String> aliases = new ArrayList<>(place.getSearchAliases() == null
                        ? List.of() : List.of(place.getSearchAliases()));
                String alias = value.path("alias").asText().trim();
                if (aliases.stream().noneMatch(stored -> stored.equalsIgnoreCase(alias))) {
                    if (aliases.size() >= MAX_SEARCH_ALIASES) {
                        throw invalid("ALIAS: the place already has " + MAX_SEARCH_ALIASES + " other names");
                    }
                    aliases.add(alias);
                    placeRepository.updateSearchAliases(placeId, aliases.toArray(String[]::new));
                }
            }
            case DESCRIPTION -> placeTranslationService.writeDescription(placeId, translationLocale,
                    value.path("description").asText(), state.displayName(), TranslationSource.MANUAL);
            case CATEGORY -> {
                PlaceGroup group = PlaceGroup.fromCode(value.path("placeGroup").asText());
                List<String> codes = new ArrayList<>();
                value.path("subTypes").forEach(node -> codes.add(node.asText()));
                placeRepository.updatePlaceGroup(placeId, group);
                placeRepository.updateSubTypes(placeId, PlaceSubType.toStorable(codes, group));
            }
            case ADDRESS -> placeRepository.updateAddress(placeId, value.path("address").asText());
            case LOCATION -> {
                placeRepository.updateLocation(placeId, value.path("latitude").decimalValue(),
                        value.path("longitude").decimalValue());
                reassignWard(place);
            }
            case OPEN_HOURS -> placeRepository.updateOpenHours(placeId, toJson(value.get("display")));
            case PHONE -> placeRepository.updatePhone(placeId, nullableText(value, "phone"));
            case WEBSITE -> placeRepository.updateWebsite(placeId, nullableText(value, "website"));
            case PRICE_RANGE -> placeRepository.updatePriceRange(placeId, value.path("display").asText());
            case BUSINESS_STATUS -> applyBusinessStatus(value, place);
            case ATTRIBUTES -> applyAttributes(value, place);
        }
        if (field.locksOnApproval()) {
            placeRepository.addLockedFields(placeId, List.of(field.name()));
        }
    }

    /** Same rule as PlaceServiceImpl.reassignWard: a point outside every boundary clears the ward. */
    private void reassignWard(Place place) {
        if (placeRepository.assignWard(place.getId()) == 0 && place.getWardCode() != null) {
            placeRepository.updateWardCode(place.getId(), null);
        }
    }

    private void applyBusinessStatus(ObjectNode value, Place place) {
        PlaceStatusReportReason reason = PlaceStatusReportReason.valueOf(value.path("reason").asText());
        switch (reason) {
            case PERMANENTLY_CLOSED -> placeRepository.updateStatusText(place.getId(),
                    PlaceBusinessStatus.PERMANENTLY_CLOSED_TEXT);
            case TEMPORARILY_CLOSED -> placeRepository.updateStatusText(place.getId(),
                    PlaceBusinessStatus.TEMPORARILY_CLOSED_TEXT);
            case REOPENED -> placeRepository.updateStatusText(place.getId(), "");
            case NOT_EXIST -> placeRepository.updateVisibility(place.getId(), PlaceVisibilityStatus.INACTIVE, null);
            case DUPLICATE -> placeRepository.updateVisibility(place.getId(), PlaceVisibilityStatus.INACTIVE,
                    UUID.fromString(value.path("duplicateOfPlaceId").asText()));
            case MOVED -> {
                // Nothing by itself: the operator adds ADDRESS / LOCATION changes in the same review.
            }
        }
    }

    private void applyAttributes(ObjectNode value, Place place) {
        ObjectNode merged = storedAttributes(place);
        value.get("attributes").fields().forEachRemaining(entry -> merged.set(entry.getKey(), entry.getValue()));
        try {
            placeRepository.updateAttributes(place.getId(),
                    PlaceAttributeCatalog.normalizeForStorage(merged, objectMapper));
        } catch (IllegalArgumentException exception) {
            throw invalid("ATTRIBUTES: " + exception.getMessage());
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw invalid("ATTRIBUTES must be valid JSON");
        }
    }

    // --- normalizers -------------------------------------------------------------------

    private ObjectNode text(String key, JsonNode value, int min, int max, boolean nullable) {
        String text = nullableText(value, key);
        ObjectNode node = objectMapper.createObjectNode();
        if (text == null) {
            if (!nullable) {
                throw new IllegalArgumentException(key + " is required");
            }
            node.putNull(key);
            return node;
        }
        String trimmed = text.trim();
        if (trimmed.length() < min || trimmed.length() > max) {
            throw new IllegalArgumentException(key + " must be " + min + " to " + max + " characters");
        }
        node.put(key, trimmed);
        return node;
    }

    private ObjectNode category(JsonNode value, Place place) {
        PlaceGroup group = PlaceGroup.fromCode(value.path("placeGroup").asText(null));
        if (group == null) {
            throw new IllegalArgumentException("placeGroup is required");
        }
        if (group == PlaceGroup.ACCOMMODATION || place.getPlaceGroup() == PlaceGroup.ACCOMMODATION) {
            if (group != place.getPlaceGroup()) {
                throw new IllegalArgumentException("a place cannot move into or out of ACCOMMODATION");
            }
        }
        List<String> codes = new ArrayList<>();
        for (JsonNode node : value.path("subTypes")) {
            PlaceSubType kind = PlaceSubType.fromCode(node.asText());
            if (kind == null || !kind.fits(group)) {
                throw new IllegalArgumentException("sub-type " + node.asText() + " does not fit " + group);
            }
            codes.add(kind.name());
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("placeGroup", group.name());
        ArrayNode subTypes = node.putArray("subTypes");
        Arrays.stream(PlaceSubType.toStorable(codes, group)).forEach(subTypes::add);
        return node;
    }

    private ObjectNode location(JsonNode value, Place place, boolean userAuthored) {
        JsonNode latitudeNode = value.get("latitude");
        JsonNode longitudeNode = value.get("longitude");
        if (latitudeNode == null || !latitudeNode.isNumber() || longitudeNode == null || !longitudeNode.isNumber()) {
            throw new IllegalArgumentException("latitude and longitude are required numbers");
        }
        BigDecimal latitude = latitudeNode.decimalValue();
        BigDecimal longitude = longitudeNode.decimalValue();
        if (latitude.abs().compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.abs().compareTo(BigDecimal.valueOf(180)) > 0) {
            throw new IllegalArgumentException("coordinates are out of range");
        }
        Double meters = GeoDistance.betweenOrNull(place.getLatitude(), place.getLongitude(), latitude, longitude);
        if (userAuthored && meters != null
                && meters > config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PIN_MOVE_METERS)) {
            throw new IllegalArgumentException("the pin may move at most "
                    + config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PIN_MOVE_METERS) + " m");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("latitude", latitude);
        node.put("longitude", longitude);
        return node;
    }

    private ObjectNode openHours(JsonNode value) {
        JsonNode structuredNode = value.get("structured");
        if (structuredNode == null || !structuredNode.isArray()) {
            throw new IllegalArgumentException("structured week is required");
        }
        List<PlaceHoursDay> structured;
        try {
            structured = objectMapper.convertValue(structuredNode, new TypeReference<List<PlaceHoursDay>>() {});
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("structured week is not readable");
        }
        Map<String, List<String>> display = PlaceOpenHoursFormatter.format(structured);
        ObjectNode node = objectMapper.createObjectNode();
        node.set("structured", objectMapper.valueToTree(structured));
        node.set("display", objectMapper.valueToTree(display));
        return node;
    }

    private ObjectNode phone(JsonNode value) {
        String phone = nullableText(value, "phone");
        ObjectNode node = objectMapper.createObjectNode();
        if (phone == null) {
            node.putNull("phone");
            return node;
        }
        String trimmed = phone.trim().replaceAll("\\s+", " ");
        if (trimmed.length() > MAX_PHONE_LENGTH || !PHONE.matcher(trimmed).matches()
                || trimmed.chars().filter(Character::isDigit).count() < 3) {
            throw new IllegalArgumentException("phone may hold digits, spaces, + - ( ) only");
        }
        node.put("phone", trimmed);
        return node;
    }

    private ObjectNode website(JsonNode value) {
        String website = nullableText(value, "website");
        ObjectNode node = objectMapper.createObjectNode();
        if (website == null) {
            node.putNull("website");
            return node;
        }
        String trimmed = website.trim();
        if (trimmed.length() > MAX_WEBSITE_LENGTH) {
            throw new IllegalArgumentException("website is too long");
        }
        String host;
        try {
            URI uri = URI.create(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null) {
                throw new IllegalArgumentException("website must be an http(s) address");
            }
            host = uri.getHost().toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("website must be an http(s) address");
        }
        for (String blocked : blockedHosts()) {
            if (host.equals(blocked) || host.endsWith("." + blocked)) {
                throw new IllegalArgumentException("website host " + host + " is not accepted");
            }
        }
        node.put("website", trimmed);
        return node;
    }

    private List<String> blockedHosts() {
        String configured = config.getText(BusinessConfigKey.PLACE_EDIT_WEBSITE_BLOCKED_HOSTS);
        if (configured == null || configured.isBlank()) {
            return List.of();
        }
        return Arrays.stream(configured.split(","))
                .map(host -> host.trim().toLowerCase(Locale.ROOT))
                .filter(host -> !host.isEmpty())
                .toList();
    }

    private ObjectNode priceRange(JsonNode value) {
        JsonNode min = value.get("min");
        JsonNode max = value.get("max");
        if (min == null || !min.canConvertToLong() || max == null || !max.canConvertToLong()) {
            throw new IllegalArgumentException("min and max are required whole numbers");
        }
        PlacePriceRange range = PlacePriceRange.builder()
                .min(min.asLong())
                .max(max.asLong())
                .currency(value.path("currency").asText(PlacePriceRangeFormatter.CURRENCY))
                .build();
        String display = PlacePriceRangeFormatter.format(range);
        ObjectNode node = objectMapper.createObjectNode();
        node.put("min", range.getMin());
        node.put("max", range.getMax());
        node.put("currency", PlacePriceRangeFormatter.CURRENCY);
        node.put("display", display);
        return node;
    }

    private ObjectNode businessStatus(JsonNode value, Place place) {
        PlaceStatusReportReason reason;
        try {
            reason = PlaceStatusReportReason.valueOf(value.path("reason").asText("").trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("reason is not a status report reason");
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.put("reason", reason.name());
        node.putNull("duplicateOfPlaceId");
        if (reason == PlaceStatusReportReason.REOPENED
                && !PlaceBusinessStatus.fromStoredStatus(place.getStatus()).isClosed()) {
            throw new IllegalArgumentException("REOPENED needs a place marked closed");
        }
        if (reason == PlaceStatusReportReason.DUPLICATE) {
            UUID original;
            try {
                original = UUID.fromString(value.path("duplicateOfPlaceId").asText(""));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("DUPLICATE needs duplicateOfPlaceId");
            }
            if (original.equals(place.getId())) {
                throw new IllegalArgumentException("a place cannot duplicate itself");
            }
            PlaceEditGuard target = placeRepository.findEditGuard(original).orElse(null);
            if (target == null || !"ACTIVE".equals(target.getVisibilityStatus())
                    || !"ACTIVE".equals(target.getLifecycleStatus())) {
                throw new IllegalArgumentException("duplicateOfPlaceId must be another ACTIVE place");
            }
            node.put("duplicateOfPlaceId", original.toString());
        }
        return node;
    }

    private ObjectNode attributes(JsonNode value, boolean userAuthored) {
        if (userAuthored) {
            throw new IllegalArgumentException("attributes are set by operators only");
        }
        JsonNode attributes = value.get("attributes");
        if (attributes == null || !attributes.isObject() || attributes.isEmpty()) {
            throw new IllegalArgumentException("attributes must be a non-empty object");
        }
        Iterator<Map.Entry<String, JsonNode>> fields = attributes.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getValue() == null || !(entry.getValue().isObject() || entry.getValue().isNull())) {
                throw new IllegalArgumentException("attribute '" + entry.getKey()
                        + "' must be an object {value, description, source_found}");
            }
        }
        ObjectNode node = objectMapper.createObjectNode();
        node.set("attributes", attributes.deepCopy());
        return node;
    }

    // --- helpers -----------------------------------------------------------------------

    private ObjectNode storedAttributes(Place place) {
        if (place.getAttributes() == null || place.getAttributes().isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            JsonNode stored = objectMapper.readTree(place.getAttributes());
            return stored != null && stored.isObject() ? (ObjectNode) stored.deepCopy() : objectMapper.createObjectNode();
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            return objectMapper.createObjectNode();
        }
    }

    private Map<String, List<String>> readHours(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, List<String>> hours = objectMapper.readValue(json, new TypeReference<>() {});
            return hours == null || hours.isEmpty() ? null : hours;
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private String toJson(JsonNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Cannot write JSON", exception);
        }
    }

    private static String nullableText(JsonNode value, String key) {
        JsonNode node = value.get(key);
        return node == null || node.isNull() ? null : node.asText();
    }

    private static boolean sameText(String a, String b) {
        String left = a == null ? "" : a.trim();
        String right = b == null ? "" : b.trim();
        return left.equals(right);
    }

    private static boolean sameTextIgnoreCase(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
