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
public class TripInvitationResponse {
    private UUID tripId;
    private String tripName;
    private String coverImageUrl;
    private String destination;
    private LocalDate startDate;
    private LocalDate endDate;
    private UserResponse invitedBy;
    private String role;
    private LocalDateTime invitedAt;
}
