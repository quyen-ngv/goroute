package com.ds.goroute.entity;

import com.ds.goroute.type.MemberRole;
import com.ds.goroute.type.MemberStatus;
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
public class TripMember {
    private UUID id;
    private UUID tripId;
    private UUID userId; // null nếu là guest
    private MemberRole role;
    private MemberStatus status;
    private UUID invitedBy;
    private LocalDateTime joinedAt;
    private LocalDateTime createdAt;
    
    // Guest member fields (khi userId = null)
    private String guestName;
    private String guestEmail; // optional, dùng để link sau này
    private String guestPhone; // optional
    private Boolean isGuest; // true nếu là guest member
}
