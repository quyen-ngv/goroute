package com.ds.goroute.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * The day-by-day plan of a NEW_TRIP or PLAN_DAYS proposal, as checked when it was proposed: what
 * applying writes, and what the card shows. Dates are ISO strings, times HH:mm, money plain numbers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssistantTripPlanResponse {
    private String name;
    /** NEW_TRIP: the destination's name; {@code cityId} when it is a catalogue city. */
    private String destination;
    private String cityId;
    private String description;
    /** NEW_TRIP: null when the user has not picked dates yet; they are chosen when applying. */
    private String startDate;
    private String endDate;
    private Integer dayCount;
    private String budget;
    private String currency;
    private String startingPointName;
    private String startingPointAddress;
    private String departureTime;
    /** How many stops applying adds. */
    private Integer itemCount;
    @Builder.Default
    private List<Day> days = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Day {
        private Integer dayNumber;
        private String title;
        @Builder.Default
        private List<Item> items = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Item {
        /** A catalogue place (its id in {@code places}); null for a leg or a stop named only. */
        private String placeId;
        private String name;
        private String startTime;
        private String endTime;
        private String category;
        private String notes;
        private String description;
        private String address;
        private String endAddress;
        private String transportMode;
        private String cost;
        private String costCurrency;
        private Boolean stay;
        private Integer untilDay;
    }
}
