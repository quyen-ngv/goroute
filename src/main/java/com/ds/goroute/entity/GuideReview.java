package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A review of a guide, tied one-to-one to a completed booking.
 *
 * <p>The tie to a booking is the whole value of the rating: only somebody who actually took
 * the tour can rate it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideReview {
    private UUID id;
    private UUID bookingId;
    private UUID guideId;
    private UUID travelerId;
    private Integer rating;
    private String comment;
    private String guideResponse;
    private LocalDateTime respondedAt;
    private Boolean isRemoved;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
