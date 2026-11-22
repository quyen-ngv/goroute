package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** What the user did with a place on their saved list: been there, or took it off. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SavedPlaceMark {
    private UUID userId;
    private String placeId;
    private LocalDateTime visitedAt;
    private LocalDateTime removedAt;
}
