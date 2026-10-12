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

    @Data
    public static class ProposalDraft {
        @Pattern(regexp = "ADD|MOVE|UPDATE_TIME|REMOVE")
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
        @Size(max = 300)
        private String summary;
    }
}
