package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One day in a guide's calendar.
 *
 * <p>Capacity is guests per day, not rooms: a guide is one person and cannot lead two
 * groups at once.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideAvailability {
    private UUID id;
    private UUID guideId;
    private LocalDate availableDate;
    private Boolean isBlocked;
    private Integer maxGuests;
    private String note;
}
