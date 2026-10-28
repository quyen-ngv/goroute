package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The worker's final answer to one question. Everything here is checked again on the server:
 * places must exist, proposals must name this trip's activities and days.
 */
@Data
public class AssistantReplyRequest {
    @Size(max = 8000)
    private String markdown;
    @Valid
    @Size(max = 12)
    private List<PlaceRef> places = new ArrayList<>();
    @Valid
    @Size(max = 10)
    private List<ProposalDraft> proposals = new ArrayList<>();
    @Size(max = 6)
    private List<@Size(max = 120) String> suggestions = new ArrayList<>();
    @Valid
    @Size(max = 6)
    private List<ActionDraft> actions = new ArrayList<>();
    @Valid
    @Size(max = 12)
    private List<CardRef> cards = new ArrayList<>();
    @Valid
    @Size(max = 8)
    private List<DestinationRef> destinations = new ArrayList<>();
    /** Questions with tap-to-answer options, asked before Pengoo plans (when, who, how long, budget). */
    @Valid
    @Size(max = 6)
    private List<QuestionDraft> questions = new ArrayList<>();
    /** Pengoo's notes for this conversation, replacing the old ones; null keeps them as they are. */
    @Size(max = 2000)
    private String memory;
    @PositiveOrZero
    private Integer inputTokens;
    @PositiveOrZero
    private Integer outputTokens;
    /**
     * Why the answer ended early: {@code CREDIT_EXHAUSTED} when the asker ran out of AI credits
     * mid-answer, so the text is what was ready by then. Null for a normal answer.
     */
    @Pattern(regexp = "CREDIT_EXHAUSTED")
    private String stopReason;

    @Data
    public static class PlaceRef {
        @Size(max = 64)
        private String placeId;
        @Size(max = 300)
        private String note;
    }

    /**
     * A card of something public Pengoo talked about (a shared trip, event, meetup, check-in or
     * dish), shown like a share: each reader's app loads it live.
     */
    @Data
    public static class CardRef {
        @Pattern(regexp = "TRIP|EVENT|MEETUP|CHECKIN|FOOD")
        private String type;
        @Size(max = 64)
        private String id;
        /** FOOD only: the city the dish is listed in. */
        @Size(max = 64)
        private String citySlug;
    }

    /**
     * A catalogue destination Pengoo recommends for a new trip: a card with its photo and a
     * "plan a trip" button that opens the create-trip screen filled in.
     */
    @Data
    public static class DestinationRef {
        @Size(max = 64)
        private String id;
        @Size(max = 300)
        private String note;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String startDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String endDate;
    }

    /** A button under the answer that opens a screen of the app; nothing happens until it is tapped. */
    @Data
    public static class ActionDraft {
        @Pattern(regexp = "CREATE_TRIP|OPEN_TRIP|OPEN_EVENT|OPEN_MEETUP")
        private String action;
        @Size(max = 80)
        private String label;
        @Size(max = 64)
        private String tripId;
        @Size(max = 64)
        private String eventId;
        @Size(max = 64)
        private String meetupId;
        /** CREATE_TRIP: the destination as the user said it; matched to a catalogue city when possible. */
        @Size(max = 80)
        private String city;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String startDate;
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String endDate;
    }

    /**
     * One question Pengoo asks with ready answers the user taps; typing an answer works as well.
     * {@code multiple} lets several options be picked together (interests, for example).
     */
    @Data
    public static class QuestionDraft {
        @Size(max = 200)
        private String text;
        @Size(max = 8)
        private List<@Size(max = 80) String> options = new ArrayList<>();
        private Boolean multiple;
    }

    /**
     * A day-by-day plan: a new trip (NEW_TRIP) or the days of an existing one (PLAN_DAYS). Values
     * are checked one by one on the server and a bad one is dropped (a stop without a usable
     * name, a time that does not parse), so the limits here only bound the request's size.
     */
    @Data
    public static class TripPlanDraft {
        @Size(max = 255)
        private String name;
        /** NEW_TRIP: the destination as the user said it; matched to a catalogue city when possible. */
        @Size(max = 120)
        private String destination;
        @Size(max = 2000)
        private String description;
        /** NEW_TRIP: the first day, when the user named it; without it the dates are picked when applying. */
        @Size(max = 10)
        private String startDate;
        /** NEW_TRIP: the trip's length in days; the days listed count when it is missing. */
        @Min(1) @Max(400)
        private Integer dayCount;
        /** Plain number or with separators ("3.605.000"). */
        @Size(max = 32)
        private String budget;
        @Size(max = 8)
        private String currency;
        @Size(max = 255)
        private String startingPointName;
        @Size(max = 500)
        private String startingPointAddress;
        /** Leaving the starting point on day 1, HH:mm. */
        @Size(max = 8)
        private String departureTime;
        @Valid
        @Size(max = 31)
        private List<PlanDay> days = new ArrayList<>();
    }

    @Data
    public static class PlanDay {
        @Min(1) @Max(400)
        private Integer dayNumber;
        /** A short theme for the day ("Săn mây Tà Xùa"). */
        @Size(max = 120)
        private String title;
        @Valid
        @Size(max = 16)
        private List<PlanItem> items = new ArrayList<>();
    }

    /** One stop or leg of a planned day: a catalogue place ({@code placeId}) or something named only. */
    @Data
    public static class PlanItem {
        @Size(max = 64)
        private String placeId;
        @Size(max = 255)
        private String name;
        @Size(max = 8)
        private String startTime;
        @Size(max = 8)
        private String endTime;
        @Size(max = 40)
        private String category;
        @Size(max = 2000)
        private String notes;
        @Size(max = 2000)
        private String description;
        @Size(max = 500)
        private String address;
        @Size(max = 500)
        private String endAddress;
        @Size(max = 16)
        private String transportMode;
        @Size(max = 32)
        private String cost;
        @Size(max = 8)
        private String costCurrency;
        /** The night's stay; {@code untilDay} is the day of check-out. */
        private Boolean stay;
        @Min(1) @Max(400)
        private Integer untilDay;
    }

    @Data
    public static class ProposalDraft {
        @Pattern(regexp = "ADD|MOVE|UPDATE_TIME|REMOVE|REPLACE|ADD_DAYS|PREPEND_DAYS|REMOVE_DAYS|SWAP_DAYS|SHIFT_DATES"
                + "|EDIT_TRIP|EDIT_ACTIVITY|NEW_ACTIVITY|ADD_EXPENSE|EDIT_EXPENSE|REMOVE_EXPENSE|ADD_NOTE|ADD_GUEST"
                + "|NEW_TRIP|PLAN_DAYS")
        private String action;
        /** NEW_TRIP / PLAN_DAYS: the plan. */
        @Valid
        private TripPlanDraft plan;
        @Size(max = 64)
        private String tripId;
        @Min(1) @Max(400)
        private Integer dayNumber;
        @Min(1) @Max(400)
        private Integer toDayNumber;
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d")
        private String startTime;
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d")
        private String endTime;
        @Size(max = 64)
        private String placeId;
        @Size(max = 64)
        private String activityId;
        @Size(max = 40)
        private String category;
        /** PREPEND_DAYS: days to add before day 1. */
        @Min(1) @Max(30)
        private Integer days;
        /** SHIFT_DATES: the trip's new first date. */
        @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        private String startDate;
        @Size(max = 300)
        private String summary;
        /** EDIT_EXPENSE / REMOVE_EXPENSE: the expense, from {@code list_expenses}. */
        @Size(max = 64)
        private String expenseId;
        /**
         * The fields to set, by name; which names an action takes is decided (and every value
         * checked) by the server, so anything else is ignored.
         */
        @Size(max = 24)
        private Map<String, Object> changes = new LinkedHashMap<>();
    }
}
