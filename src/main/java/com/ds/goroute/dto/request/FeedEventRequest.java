package com.ds.goroute.dto.request;

import com.ds.goroute.type.FeedItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * A batch of what the viewer did with Feed posts (POST /v1/api/feed/events, V267). IMPRESSION
 * and OPEN also count in feed_impressions, as /feed/impressions does; any other event (GLANCE,
 * LIKE, SAVE, HIDE, ...) only goes to the event log.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedEventRequest {

    public static final int MAX_ITEMS = 50;
    public static final String IMPRESSION = "IMPRESSION";
    public static final String OPEN = "OPEN";

    @NotEmpty
    @Size(max = MAX_ITEMS)
    @Valid
    private List<Item> items;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        @NotNull
        private FeedItemType type;
        @NotNull
        private UUID id;
        /** Upper-case name; an open list. */
        @NotNull
        @Pattern(regexp = "[A-Z_]{1,32}")
        private String event;
        /** for-you, near, friends, ... */
        @Pattern(regexp = "[a-z0-9-]{1,32}")
        private String surface;
        @Min(0)
        @Max(100_000)
        private Integer position;
        @Min(0)
        @Max(600_000)
        private Integer dwellMs;
    }
}
