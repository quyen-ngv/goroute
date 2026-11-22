package com.ds.goroute.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Traveller preferences answered while creating a trip. Every field is optional: a skipped
 * question is stored as null/empty, never as a default, so analytics can tell "chose BALANCED"
 * from "did not answer". Codes are case-insensitive on input and stored upper-case.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPreferencesRequest {
    @Pattern(regexp = "(?i)SOLO|COUPLE|FAMILY|FRIENDS")
    private String companion;

    private Boolean withChildren;
    private Boolean withElderly;

    @Pattern(regexp = "(?i)RELAXED|BALANCED|EAGER")
    private String pace;

    @Pattern(regexp = "(?i)FAMOUS|BALANCED|HIDDEN_GEMS")
    private String discoveryStyle;

    /** Place groups and activity codes, e.g. FOOD_AND_DRINK, ADVENTURE. */
    @Size(max = 12)
    private List<@Pattern(regexp = "[A-Za-z_]{1,40}") String> interests;

    /** Dietary codes, e.g. VEGETARIAN, NO_PORK. */
    @Size(max = 10)
    private List<@Pattern(regexp = "[A-Za-z_]{1,40}") String> dietary;

    @Size(max = 120)
    private String dietaryNote;

    /** The traveller's own words about the trip (also sent to the AI planner). */
    @Size(max = 2000)
    private String preferenceText;
}
