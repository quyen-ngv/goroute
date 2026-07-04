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
public class TripMemberResponse {
    private UUID id;
    private UserResponse user;
    private String role;
    private String status;
    private UUID invitedBy;
    private LocalDateTime joinedAt;
    private Boolean isGuest; // true nếu là guest member
}
