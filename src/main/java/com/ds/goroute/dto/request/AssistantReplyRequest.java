package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

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
    @Size(max = 6)
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
    /** Pengoo's notes for this conversation, replacing the old ones; null keeps them as they are. */
    @Size(max = 2000)
    private String memory;
    @PositiveOrZero
    private Integer inputTokens;
    @PositiveOrZero
    private Integer outputTokens;

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

    @Data
    public static class ProposalDraft {
        @Pattern(regexp = "ADD|MOVE|UPDATE_TIME|REMOVE|REPLACE|ADD_DAYS|PREPEND_DAYS|REMOVE_DAYS|SWAP_DAYS|SHIFT_DATES")
        private String action;
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
    }
}
