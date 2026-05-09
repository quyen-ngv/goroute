package com.ds.goroute.entity;

import com.ds.goroute.type.FlagSeverity;
import com.ds.goroute.type.ReviewFlagType;
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
public class ReviewFlag {
    private UUID id;
    private UUID reviewId;
    private ReviewFlagType flagType;
    private FlagSeverity severity;
    private String reason;
    private String metadata; // JSON
    private LocalDateTime createdAt;
}
