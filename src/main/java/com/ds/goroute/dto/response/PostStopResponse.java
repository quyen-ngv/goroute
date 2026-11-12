package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One stop of the trip a story is about, as the story's {@code ### [name](activity:<id>)} heading
 * draws it: read live from the activity, so a renamed or rescheduled stop shows its current name
 * and time without the author re-saving the post. Only what a reader of the story may know -- no
 * cost, no notes, no booking.
 */
@Value
@Builder
public class PostStopResponse {
    UUID activityId;
    Integer dayNumber;
    Integer sortOrder;
    String name;
    String address;
    /** A transport leg's destination; null for a stop. */
    String endAddress;
    BigDecimal lat;
    BigDecimal lng;
    LocalTime startTime;
    LocalTime endTime;
    String category;
    String transportMode;
    /** The catalogue place behind the stop, when it has one. */
    UUID placeId;
    String photoUrl;
    boolean isAccommodation;
    boolean isStartingPoint;
}
