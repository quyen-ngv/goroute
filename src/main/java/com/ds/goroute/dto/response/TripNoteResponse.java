package com.ds.goroute.dto.response;

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
public class TripNoteResponse {
    private UUID id;
    private UUID tripId;
    private UUID activityId;  // Optional: for activity-specific notes
    private UserResponse user;
    private String content;
    private LocalDateTime createdAt;
}
