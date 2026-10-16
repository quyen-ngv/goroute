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
public class TripResponse {
    private UUID id;
    private String name;
    private String coverImageUrl;
    private List<String> memoryImageUrls;
    private List<MemoryImageResponse> memoryImageUrlsV2;
    private String destination;
    private BigDecimal lat;
    private BigDecimal lng;
    private List<TripDestinationResponse> destinations;
    private String routeSummary;
    private LocalDate startDate;
    private LocalDate endDate;
    /**
     * IANA zone of the destination; the itinerary's dates and times are wall-clock times there.
     * Null while unknown (destination abroad and not looked up yet, or no destination).
     */
    private String timezone;
    private String status;
    private BigDecimal budget;
    private String currency;
    private String visibility;
    private String shareCode;
    private Boolean shareExpenses;
    private Boolean shareNotes;
    private String description;
    private TripStatsResponse stats;
    private String userRole; // Role of current user in this trip (OWNER, EDITOR, VIEWER)
    private UUID ownerId;
    private String ownerName;
    private String ownerAvatarUrl;
    private Integer viewCount;
    private Integer copyCount;
    private Integer helpfulVotes;
    private Integer unhelpfulVotes;
    /**
     * Active comments on this trip's thread. Filled only by the lists that render trips as
     * posts (GET /trips, GET /users/{userId}/trips); null on every other trip response.
     */
    private Integer commentCount;
    private LocalDateTime publicSharedAt;
}
