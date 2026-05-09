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
public class ReviewHelpfulVote {
    private UUID reviewId;
    private UUID userId;
    private boolean isHelpful; // true = helpful, false = unhelpful
    private LocalDateTime createdAt;
}
