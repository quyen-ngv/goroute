package com.ds.goroute.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards {@code scripts/geo/remap_63_to_34.json}, the hand-maintained list the backfill
 * uses to retire the old province codes. A wrong line there silently moves every passport
 * event of a province, so the shape of the file is checked here rather than trusted.
 */
@DisplayName("Province remap 63 -> 34")
class ProvinceRemapTest {

    private static final Set<String> CURRENT_PROVINCE_CODES = Set.of(
            "01", "04", "08", "11", "12", "14", "15", "19", "20", "22", "24", "25", "31", "33", "37",
            "38", "40", "42", "44", "46", "48", "51", "52", "56", "66", "68", "75", "79", "80", "82",
            "86", "91", "92", "96");

    @Test
    @DisplayName("every one of the 63 old codes maps exactly once onto one of the 34 current codes")
    void everyOldCodeMapsOntoACurrentCode() throws Exception {
        JsonNode remap = new ObjectMapper().readTree(Files.readString(Path.of("scripts/geo/remap_63_to_34.json")));
        Set<String> oldCodes = new HashSet<>();
        Set<String> targets = new HashSet<>();

        for (JsonNode row : remap.get("old")) {
            assertThat(oldCodes.add(row.get("code").asText()))
                    .as("old code %s listed twice", row.get("code").asText()).isTrue();
            assertThat(CURRENT_PROVINCE_CODES)
                    .as("%s (%s) -> %s", row.get("code").asText(), row.get("name").asText(), row.get("newCode").asText())
                    .contains(row.get("newCode").asText());
            targets.add(row.get("newCode").asText());
        }

        assertThat(oldCodes).hasSize(63);
        assertThat(targets).as("every current province absorbs at least its own old code")
                .containsExactlyInAnyOrderElementsOf(CURRENT_PROVINCE_CODES);
        assertThat(CURRENT_PROVINCE_CODES).hasSize(34);
    }
}
