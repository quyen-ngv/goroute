package com.ds.goroute.dto.request;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
public class AiTripCommitRequest {
    @NotBlank private String attemptId;
    @ModeratedText(contentType = ModeratedContentType.TRIP, visibility = ModerationVisibility.PUBLIC)
    private String tripDescription;
    @NotEmpty @Valid private List<Item> items;

    @Data
    public static class Item {
        @NotBlank private String type;
        private UUID placeId;
        @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
        @NotBlank private String name;
        @Min(1) private int dayNumber;
        @Min(0) private int sortOrder;
        private LocalTime startTime;
        private LocalTime endTime;
        private Integer endDayNumber;
        @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
        private String endAddress;
        private BigDecimal endLatitude;
        private BigDecimal endLongitude;
        private String category;
        private String transportMode;
        private String durationToNext;
        private Integer durationValueToNext;
        private String distanceToNext;
        private Integer distanceValueToNext;
        @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
        private String description;
        @ModeratedText(contentType = ModeratedContentType.ACTIVITY, visibility = ModerationVisibility.GROUP)
        private String notes;
        private String candidateId;
        /** Set only after Java has verified an external Goong candidate. */
        private String externalPlaceResolution;
        private String optionGroupId;
        private Integer optionIndex;
        private String relation;
        private String sourceSocialJobId;
        private String sourceSocialCandidateRef;
    }
}
