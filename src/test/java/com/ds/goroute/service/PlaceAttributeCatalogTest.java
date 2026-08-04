package com.ds.goroute.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceAttributeCatalogTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void defaultCatalogContainsAllSchemaFields() {
        JsonNode defaults = PlaceAttributeCatalog.defaultAttributes();

        assertEquals(203, PlaceAttributeCatalog.definitions().size());
        assertTrue(defaults.has("finding_difficulty"));
        assertTrue(defaults.has("food_quality"));
        assertTrue(defaults.has("late_night_available"));
        assertTrue(defaults.has("smoking_exposure_level"));
        assertTrue(defaults.has("ambience"));
        assertTrue(defaults.get("finding_difficulty").get("value").isNull());
        assertTrue(defaults.get("finding_difficulty").get("description").isNull());
        assertFalse(defaults.get("finding_difficulty").get("source_found").asBoolean());
        assertFalse(defaults.get("kid_friendly").has("description"));
    }

    @Test
    void publicAttributesOnlyKeepsFieldsWithInformation() throws Exception {
        JsonNode stored = objectMapper.readTree("""
                {
                  "finding_difficulty": {"value": "HARD", "description": "Small alley", "source_found": true},
                  "food_quality": {"value": null, "description": null, "source_found": false},
                  "kid_friendly": {"value": false, "source_found": true}
                }
                """);

        JsonNode publicAttributes = PlaceAttributeCatalog.publicAttributes(stored);

        assertNotNull(publicAttributes);
        assertTrue(publicAttributes.has("finding_difficulty"));
        assertTrue(publicAttributes.get("finding_difficulty").get("source_found").asBoolean());
        assertTrue(publicAttributes.get("kid_friendly").get("source_found").asBoolean());
        assertFalse(publicAttributes.get("kid_friendly").has("description"));
        assertNull(publicAttributes.get("food_quality"));
    }

    @Test
    void normalizingFullAdminPayloadKeepsOnlyPopulatedFields() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {
                  "finding_difficulty": {"value": "HARD", "description": "Small alley", "source_found": true},
                  "food_quality": {"value": null, "description": null, "source_found": false},
                  "ambience": {"value": ["LOCAL", "CASUAL"], "description": "Local and casual", "source_found": true}
                }
                """);

        JsonNode normalized = objectMapper.readTree(
                PlaceAttributeCatalog.normalizeForStorage(input, objectMapper));

        assertEquals(2, normalized.size());
        assertEquals("HARD", normalized.get("finding_difficulty").get("value").asText());
        assertTrue(normalized.get("finding_difficulty").get("source_found").asBoolean());
        assertEquals(2, normalized.get("ambience").get("value").size());
    }

    @Test
    void legacyNightLifeKeyIsMigratedToDocumentedKey() throws Exception {
        JsonNode input = objectMapper.readTree("""
                {"late_night": {"value": true, "source_found": true}}
                """);

        JsonNode normalized = objectMapper.readTree(
                PlaceAttributeCatalog.normalizeForStorage(input, objectMapper));

        assertTrue(normalized.has("late_night_available"));
        assertFalse(normalized.has("late_night"));
    }
}
