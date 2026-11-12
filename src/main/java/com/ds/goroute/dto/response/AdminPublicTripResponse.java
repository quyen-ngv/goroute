package com.ds.goroute.dto.response;

import com.ds.goroute.type.TripVisibility;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the admin public-trip console: enough to judge a hide/show or unpublish decision. */
@Data
@Builder
public class AdminPublicTripResponse {

    private UUID id;
    private String name;
    private String description;
    private String coverImageUrl;
    private String destination;
    private TripVisibility visibility;
    private LocalDate startDate;
    private LocalDate endDate;

    private UUID ownerId;
    private String ownerDisplayName;
    private String ownerEmail;
    private String ownerAvatarUrl;

    private int activityCount;
    private int memoryCount;
    private int viewCount;
    private int copyCount;
    private int helpfulVotes;

    /** Taken down (content_takedowns, type TRIP): out of every public surface, still PUBLIC/SHARED. */
    private boolean hidden;
    private LocalDateTime publicSharedAt;
    private LocalDateTime createdAt;
}
