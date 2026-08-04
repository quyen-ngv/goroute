package com.ds.goroute.service;

import com.ds.goroute.dto.response.PlaceAttributeDefinition;
import com.ds.goroute.type.PlaceAttributeGroup;
import com.ds.goroute.type.PlaceAttributeType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Single source of truth for schema v1 place attribute keys and types. */
public final class PlaceAttributeCatalog {

    private static final List<PlaceAttributeDefinition> DEFINITIONS = buildDefinitions();

    private PlaceAttributeCatalog() {
    }

    public static List<PlaceAttributeDefinition> definitions() {
        return DEFINITIONS;
    }

    public static ObjectNode defaultAttributes() {
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        for (PlaceAttributeDefinition definition : DEFINITIONS) {
            root.set(definition.key(), emptyAttribute(definition));
        }
        return root;
    }

    public static ObjectNode publicAttributes(JsonNode stored) {
        ObjectNode result = JsonNodeFactory.instance.objectNode();
        if (stored == null || !stored.isObject()) {
            return result;
        }
        stored.fields().forEachRemaining(entry -> {
            String key = canonicalKey(entry.getKey());
            PlaceAttributeDefinition definition = findDefinition(key);
            JsonNode attribute = canonicalAttribute(definition, entry.getValue());
            if (containsInformation(definition, attribute)) {
                result.set(key, attribute);
            }
        });
        return result.isEmpty() ? null : result;
    }

    public static ObjectNode adminAttributes(JsonNode stored) {
        ObjectNode result = defaultAttributes();
        if (stored == null || !stored.isObject()) {
            return result;
        }
        stored.fields().forEachRemaining(entry -> {
            String key = canonicalKey(entry.getKey());
            PlaceAttributeDefinition definition = findDefinition(key);
            result.set(key, canonicalAttribute(definition, entry.getValue()));
        });
        return result;
    }

    /** Keeps the database sparse while admin submits the complete default catalog. */
    public static String normalizeForStorage(JsonNode input, ObjectMapper objectMapper) throws JsonProcessingException {
        if (input == null || input.isNull()) {
            return null;
        }
        if (!input.isObject()) {
            throw new IllegalArgumentException("attributes must be a JSON object");
        }

        ObjectNode normalized = JsonNodeFactory.instance.objectNode();
        input.fields().forEachRemaining(entry -> {
            String key = canonicalKey(entry.getKey());
            if ("late_night".equals(entry.getKey()) && input.has("late_night_available")) {
                return;
            }
            JsonNode attribute = entry.getValue();
            validateAttribute(key, attribute);
            PlaceAttributeDefinition definition = findDefinition(key);
            JsonNode compact = canonicalAttribute(definition, attribute);
            if (containsInformation(definition, compact)) {
                normalized.set(key, compact);
            }
        });
        return normalized.isEmpty() ? null : objectMapper.writeValueAsString(normalized);
    }

    private static void validateAttribute(String key, JsonNode attribute) {
        if (attribute == null || !attribute.isObject()) {
            throw new IllegalArgumentException("attribute '" + key + "' must be an object");
        }
        PlaceAttributeDefinition definition = findDefinition(key);
        JsonNode description = attribute.get("description");
        if (definition == null || definition.type() != PlaceAttributeType.BOOLEAN) {
            if (description != null && !description.isNull() && !description.isTextual()) {
                throw new IllegalArgumentException("attribute '" + key + ".description' must be text");
            }
        }
        JsonNode sourceFound = attribute.get("source_found");
        if (sourceFound != null && !sourceFound.isNull() && !sourceFound.isBoolean()) {
            throw new IllegalArgumentException("attribute '" + key + ".source_found' must be boolean");
        }
        if (definition == null) {
            return;
        }

        JsonNode value = attribute.get("value");
        if (value == null || value.isNull()) {
            return;
        }
        if (definition.type() == PlaceAttributeType.BOOLEAN && !value.isBoolean()) {
            throw new IllegalArgumentException("attribute '" + key + ".value' must be boolean");
        }
        if (definition.multiple()) {
            if (!value.isArray()) {
                throw new IllegalArgumentException("attribute '" + key + ".value' must be an array");
            }
            for (JsonNode item : value) {
                if (!item.isTextual() || !definition.allowedValues().contains(item.asText())) {
                    throw new IllegalArgumentException("attribute '" + key + ".value' contains an invalid value");
                }
            }
            return;
        }
        if (!definition.allowedValues().isEmpty()
                && (!value.isTextual() || !definition.allowedValues().contains(value.asText()))) {
            throw new IllegalArgumentException("attribute '" + key + ".value' contains an invalid value");
        }
    }

    private static PlaceAttributeDefinition findDefinition(String key) {
        return DEFINITIONS.stream()
                .filter(item -> item.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    private static String canonicalKey(String key) {
        return "late_night".equals(key) ? "late_night_available" : key;
    }

    private static ObjectNode emptyAttribute(PlaceAttributeDefinition definition) {
        ObjectNode attribute = JsonNodeFactory.instance.objectNode();
        attribute.putNull("value");
        if (definition == null || definition.type() != PlaceAttributeType.BOOLEAN) {
            attribute.putNull("description");
        }
        attribute.put("source_found", false);
        return attribute;
    }

    private static ObjectNode canonicalAttribute(PlaceAttributeDefinition definition, JsonNode source) {
        ObjectNode attribute = emptyAttribute(definition);
        if (source == null || !source.isObject()) {
            return attribute;
        }
        JsonNode value = source.get("value");
        if (value != null && !value.isNull()) {
            attribute.set("value", value.deepCopy());
        }
        if (definition == null || definition.type() != PlaceAttributeType.BOOLEAN) {
            JsonNode description = source.get("description");
            if (description != null && !description.isNull()) {
                attribute.set("description", description.deepCopy());
            }
        }
        JsonNode sourceFound = source.get("source_found");
        attribute.put("source_found", sourceFound == null || sourceFound.isNull()
                ? containsInformation(definition, source)
                : sourceFound.asBoolean());
        return attribute;
    }

    private static boolean containsInformation(PlaceAttributeDefinition definition, JsonNode attribute) {
        if (attribute == null || attribute.isNull()) {
            return false;
        }
        JsonNode value = attribute.get("value");
        if (value != null && !value.isNull()) {
            if (value.isTextual()) {
                return !value.asText().isBlank();
            }
            if (value.isArray()) {
                return !value.isEmpty();
            }
            return true;
        }
        if (definition != null && definition.type() == PlaceAttributeType.BOOLEAN) {
            return false;
        }
        JsonNode description = attribute.get("description");
        return description != null && description.isTextual() && !description.asText().isBlank();
    }

    private static List<PlaceAttributeDefinition> buildDefinitions() {
        List<PlaceAttributeDefinition> result = new ArrayList<>();
        add(result, PlaceAttributeGroup.UNIVERSAL, PlaceAttributeType.BOOLEAN,
                "kid_friendly", "elderly_friendly", "wheelchair_accessible", "stroller_friendly", "pet_friendly",
                "solo_friendly", "couple_friendly", "group_friendly", "indoor_available", "outdoor_available",
                "restroom_available", "parking_available", "public_transport_accessible", "ride_hailing_accessible",
                "free_entry", "ticket_required", "reservation_required", "advance_booking_recommended");
        add(result, PlaceAttributeGroup.UNIVERSAL, PlaceAttributeType.DIFFICULTY,
                "finding_difficulty", "access_difficulty");
        add(result, PlaceAttributeGroup.UNIVERSAL, PlaceAttributeType.LEVEL,
                "physical_effort_level", "walking_level", "stairs_level", "crowd_level", "queue_level", "noise_level",
                "weather_dependency", "local_authenticity", "local_life_exposure", "touristy_level",
                "cultural_significance", "historical_significance", "educational_value", "relaxation_level",
                "social_energy_level", "romantic_level", "entertainment_level", "scam_price_risk");
        add(result, PlaceAttributeGroup.UNIVERSAL, PlaceAttributeType.QUALITY,
                "rain_suitability", "hot_weather_suitability", "cleanliness", "safety", "value_for_money",
                "photogenic", "scenic", "uniqueness");
        add(result, PlaceAttributeGroup.UNIVERSAL, PlaceAttributeType.TIME_COMMITMENT, "time_commitment");
        add(result, PlaceAttributeGroup.UNIVERSAL, PlaceAttributeType.PRICE_LEVEL, "price_level");

        add(result, PlaceAttributeGroup.VIETNAM, PlaceAttributeType.BOOLEAN,
                "alley_location", "motorbike_parking", "street_side_location", "street_seating", "air_conditioning",
                "cash_only", "cashless_payment", "qr_bank_transfer", "english_support");
        add(result, PlaceAttributeGroup.VIETNAM, PlaceAttributeType.DIFFICULTY,
                "alley_access_difficulty", "car_access_difficulty", "ride_hailing_dropoff_difficulty");
        add(result, PlaceAttributeGroup.VIETNAM, PlaceAttributeType.COMFORT, "seating_comfort", "heat_comfort");
        add(result, PlaceAttributeGroup.VIETNAM, PlaceAttributeType.RELIABILITY, "opening_hours_reliability");

        add(result, PlaceAttributeGroup.FOOD, PlaceAttributeType.BOOLEAN,
                "dine_in", "takeout", "delivery", "reservable", "outdoor_seating", "high_chair_available",
                "children_menu_available", "waiter_service", "serves_breakfast", "serves_brunch", "serves_lunch",
                "serves_dinner", "serves_late_night", "serves_coffee", "serves_dessert", "serves_alcohol",
                "vegetarian_options", "vegan_options", "halal_options", "pork_free_options", "beef_free_options",
                "seafood_free_options", "gluten_free_options", "allergy_accommodation", "english_menu", "picture_menu");
        add(result, PlaceAttributeGroup.FOOD, PlaceAttributeType.QUALITY,
                "food_quality", "food_hygiene", "presentation_quality", "service_quality", "menu_variety");
        add(result, PlaceAttributeGroup.FOOD, PlaceAttributeType.SPEED, "service_speed");
        add(result, PlaceAttributeGroup.FOOD, PlaceAttributeType.SIZE, "portion_size");
        add(result, PlaceAttributeGroup.FOOD, PlaceAttributeType.LEVEL,
                "spice_level", "local_food_authenticity", "street_food_authenticity", "sell_out_risk",
                "shared_table_likelihood", "smoke_exposure_level");
        add(result, PlaceAttributeGroup.FOOD, PlaceAttributeType.DIFFICULTY, "ordering_difficulty");

        add(result, PlaceAttributeGroup.HOTEL, PlaceAttributeType.BOOLEAN,
                "pool_available", "gym_available", "spa_available", "breakfast_available", "breakfast_included",
                "elevator_available", "workspace_available", "laundry_available", "luggage_storage_available",
                "airport_transfer_available", "front_desk_24h", "family_room_available", "late_checkin_available",
                "motorbike_rental_available");
        add(result, PlaceAttributeGroup.HOTEL, PlaceAttributeType.COMFORT, "room_comfort");
        add(result, PlaceAttributeGroup.HOTEL, PlaceAttributeType.QUALITY,
                "sleep_quality", "room_view", "soundproofing", "housekeeping", "staff_service",
                "location_convenience", "checkin_flexibility", "facility_quality");
        add(result, PlaceAttributeGroup.HOTEL, PlaceAttributeType.SIZE, "room_size");

        add(result, PlaceAttributeGroup.ATTRACTION, PlaceAttributeType.BOOLEAN,
                "guided_tour_available", "self_guided_friendly", "audio_guide_available", "interactive_experience",
                "photo_restriction", "dress_code_required");
        add(result, PlaceAttributeGroup.ATTRACTION, PlaceAttributeType.LEVEL, "iconic_level", "interactive_level");

        add(result, PlaceAttributeGroup.NATURE, PlaceAttributeType.BOOLEAN,
                "hiking_available", "cycling_available", "swimming_available", "boating_available", "picnic_available",
                "camping_available", "wildlife_viewing_available", "viewpoint_available", "food_available_on_site",
                "shower_available", "changing_room_available");
        add(result, PlaceAttributeGroup.NATURE, PlaceAttributeType.DIFFICULTY, "terrain_difficulty");
        add(result, PlaceAttributeGroup.NATURE, PlaceAttributeType.LEVEL,
                "remoteness_level", "shade_level", "heat_exposure_level", "seasonal_dependency", "slippery_risk", "water_risk");
        add(result, PlaceAttributeGroup.NATURE, PlaceAttributeType.QUALITY, "mobile_signal_quality");

        add(result, PlaceAttributeGroup.THEME_PARK, PlaceAttributeType.BOOLEAN,
                "rides_available", "water_activities_available", "indoor_activities_available", "toddler_friendly",
                "teen_friendly", "height_restrictions_exist", "locker_available", "stroller_rental_available");
        add(result, PlaceAttributeGroup.THEME_PARK, PlaceAttributeType.LEVEL, "thrill_level");

        add(result, PlaceAttributeGroup.SHOPPING, PlaceAttributeType.BOOLEAN,
                "bargaining_available", "bargaining_expected", "fixed_price_common", "local_products_available",
                "souvenirs_available", "food_available");
        add(result, PlaceAttributeGroup.SHOPPING, PlaceAttributeType.QUALITY, "shopping_variety", "local_product_quality");

        add(result, PlaceAttributeGroup.NIGHTLIFE, PlaceAttributeType.BOOLEAN,
                "alcohol_available", "live_music_available", "dance_floor_available", "late_night_available",
                "reservation_available", "dress_code_required", "cover_charge_required", "age_restriction");
        add(result, PlaceAttributeGroup.NIGHTLIFE, PlaceAttributeType.LEVEL, "party_intensity", "music_volume_level");
        add(result, PlaceAttributeGroup.NIGHTLIFE, PlaceAttributeType.QUALITY, "drink_quality", "service_quality");
        add(result, PlaceAttributeGroup.NIGHTLIFE, PlaceAttributeType.LEVEL, "smoking_exposure_level");

        add(result, PlaceAttributeGroup.WELLNESS, PlaceAttributeType.BOOLEAN,
                "appointment_required", "walk_in_available", "private_room_available", "shower_available", "sauna_available", "massage_available");
        add(result, PlaceAttributeGroup.WELLNESS, PlaceAttributeType.QUALITY, "privacy_quality", "hygiene", "staff_skill");

        add(result, PlaceAttributeGroup.TEMPLE, PlaceAttributeType.BOOLEAN,
                "active_worship_place", "visitors_allowed", "dress_code_required", "shoe_removal_required",
                "photo_restriction", "silence_expected", "guided_tour_available");
        add(result, PlaceAttributeGroup.TEMPLE, PlaceAttributeType.LEVEL,
                "spiritual_atmosphere", "historical_significance", "cultural_significance", "architectural_interest");
        add(result, PlaceAttributeGroup.TEMPLE, PlaceAttributeType.QUALITY, "photogenic");

        add(result, PlaceAttributeGroup.TRANSPORT, PlaceAttributeType.BOOLEAN,
                "seating_available", "luggage_storage_available", "public_transport_connection", "open_24h");
        add(result, PlaceAttributeGroup.TRANSPORT, PlaceAttributeType.QUALITY,
                "navigation_ease", "connection_convenience");
        add(result, PlaceAttributeGroup.TRANSPORT, PlaceAttributeType.COMFORT, "waiting_comfort");

        add(result, PlaceAttributeGroup.AMBIENCE, PlaceAttributeType.AMBIENCE_TAG, "ambience");
        return List.copyOf(result);
    }

    private static void add(List<PlaceAttributeDefinition> target, PlaceAttributeGroup group,
                            PlaceAttributeType type, String... keys) {
        Arrays.stream(keys).forEach(key -> {
            if (target.stream().noneMatch(item -> item.key().equals(key))) {
                target.add(new PlaceAttributeDefinition(
                        key, humanize(key), group, group.getLabel(), type, type.getAllowedValues(), type.getRankByValue(), type.isMultiple()));
            }
        });
    }

    private static String humanize(String key) {
        StringBuilder result = new StringBuilder();
        for (String word : key.split("_")) {
            if (word.isBlank()) {
                continue;
            }
            if (result.length() > 0) {
                result.append(' ');
            }
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(word.substring(1).toLowerCase(Locale.ROOT));
        }
        return result.toString();
    }
}
