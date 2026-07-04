package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TripAccessRequestResponse {
    private UUID memberId;
    private UUID tripId;
    private String tripName;
    private String coverImageUrl;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private String role;
    private LocalDateTime requestedAt;
}
