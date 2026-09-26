package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicTripResponse {
    private UUID id;
    private String name;
    private String coverImageUrl;
    private List<String> memoryImageUrls;
    private List<MemoryImageResponse> memoryImageUrlsV2;
    private String description;
    private String destination;
    private BigDecimal lat;
    private BigDecimal lng;
    private List<TripDestinationResponse> destinations;
    private String routeSummary;
    private LocalDate startDate;
    private LocalDate endDate;
    private String currency;
    private UUID ownerId;
    private String ownerName;
    private String ownerAvatarUrl;
    private List<PublicActivityResponse> activities;
    private List<PublicExpenseResponse> expenses;
    private List<PublicNoteResponse> notes;

    /**
     * Public check-ins made during the trip that hang off no particular stop.
     *
     * <p>The ones that do belong to a stop travel on {@link PublicActivityResponse}
     * instead, so a reader never meets the same post twice on one page.
     */
    private List<UserCheckinResponse> checkins;

    private Integer viewCount;
    private Integer copyCount;
    private Integer helpfulVotes;
    private Integer unhelpfulVotes;
    private Boolean hasVotedHelpful;
    private Boolean isOwnTrip;
    private Integer totalMembers;
    /** Number of activities on the trip. Carried separately because list payloads leave { activities} out. */
    private Integer totalActivities;
    private LocalDateTime publicSharedAt;
}
