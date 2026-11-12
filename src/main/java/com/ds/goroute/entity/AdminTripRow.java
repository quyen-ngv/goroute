package com.ds.goroute.entity;

import com.ds.goroute.type.TripVisibility;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One shared trip as the admin public-trip console lists it ({@code TripMapper.selectAdminSharedTrips}). */
@Data
public class AdminTripRow {
    private UUID id;
    private String name;
    private String description;
    private String coverImageUrl;
    private String destination;
    private TripVisibility visibility;
    private UUID ownerId;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer activityCount;
    private Integer memoryCount;
    private Integer viewCount;
    private Integer copyCount;
    private Integer helpfulVotes;
    private Boolean hidden;
    private LocalDateTime publicSharedAt;
    private LocalDateTime createdAt;
}
