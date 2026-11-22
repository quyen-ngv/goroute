package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Row of {@code trip_preferences}, plus its {@code trip_preference_tags}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripPreference {
    public static final String SOURCE_MANUAL = "MANUAL";
    public static final String SOURCE_AI = "AI";
    public static final String TAG_INTEREST = "INTEREST";
    public static final String TAG_DIETARY = "DIETARY";

    private UUID tripId;
    private String source;
    private String companion;
    private boolean withChildren;
    private boolean withElderly;
    private String pace;
    private String discoveryStyle;
    private String dietaryNote;
    private String preferenceText;
    private List<String> interests;
    private List<String> dietary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
