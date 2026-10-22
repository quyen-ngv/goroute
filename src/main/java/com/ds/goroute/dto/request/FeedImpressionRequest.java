package com.ds.goroute.dto.request;

import com.ds.goroute.type.FeedItemType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Posts the viewer had on screen since the last batch (POST /v1/api/feed/impressions). The app
 * counts a post once it is at least half visible for at least a second, and sends opened=true
 * when the viewer opened its detail screen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedImpressionRequest {

    public static final int MAX_ITEMS = 50;

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

        /** How long the post stayed on screen, capped at ten minutes; 0 when unknown. */
        @Min(0)
        @Max(600_000)
        private int dwellMs;

        private boolean opened;
    }
}
