package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** A trip itinerary occurrence of a place or activity-booking catalog item. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedItemTripResponse {
    private String itemId;
    private String itemType;
    private UUID tripId;
    private String tripName;
    private String tripCoverImageUrl;
    private String tripDestination;
    private UUID activityId;
    private Integer dayNumber;
}
