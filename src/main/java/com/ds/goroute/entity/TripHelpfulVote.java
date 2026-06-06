package com.ds.goroute.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripHelpfulVote {
    private UUID tripId;
    private UUID userId;
    private boolean isHelpful;
    private LocalDateTime createdAt;
}
