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
    /**
     * Deprecated: the urls of {@link #memoryImageUrlsV2}, kept only for app versions that
     * predate V2. Remove once those versions are gone. Same cap as V2 in list payloads.
     */
    private List<String> memoryImageUrls;
    /**
     * Memory photos. Search and Friends-tab cards carry at most the first
     * {@code TripServiceImpl.CARD_MEMORY_PREVIEW_LIMIT} (6); the trip detail carries all of
     * them. {@link #totalMemoryCount} is the full number either way.
     */
    private List<MemoryImageResponse> memoryImageUrlsV2;
    /** Every memory photo of the trip, including those a list card leaves out. */
    private Integer totalMemoryCount;
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
    /** Active comments on this trip's thread, the number GET /content-comments/count returns. */
    private int commentCount;
    private Boolean isOwnTrip;
    private Integer totalMembers;
    /** Number of activities on the trip. Carried separately because list payloads leave { activities} out. */
    private Integer totalActivities;
    private LocalDateTime publicSharedAt;
    /** Home "Friends" tab only (GET /trips/following); the same ranks as UserCheckinResponse#focusRank. */
    private Integer focusRank;
}
