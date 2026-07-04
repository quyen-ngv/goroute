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
public class TripBook {
    private UUID id;
    private UUID tripId;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
