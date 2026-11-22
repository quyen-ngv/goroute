package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;
import java.util.UUID;

/** The trip a TRIP_STORY is about, shown only while the reader may open that trip. */
@Value
@Builder
public class PostTripResponse {
    UUID id;
    String name;
    LocalDate startDate;
    LocalDate endDate;
    /** The viewer may change the trip's itinerary (owner or editor): the editor offers trip edits. */
    boolean canEdit;
}
