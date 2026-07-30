package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
public class UpsertOrganizationMemberRequest {
    @NotNull
    private UUID userId;
    @NotNull
    @Pattern(regexp = "PARTNER_ADMIN|PROPERTY_MANAGER|REVENUE_MANAGER|RESERVATION_AGENT|FRONT_DESK|HOUSEKEEPING|FINANCE|CONTENT_MANAGER|TOUR_OPERATOR|GUIDE|TICKET_SCANNER|VIEWER")
    private String roleCode;
    @Pattern(regexp = "INVITED|ACTIVE|SUSPENDED|ACCESS_EXPIRED|DEACTIVATED")
    private String memberStatus = "ACTIVE";
    private List<String> permissions;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}
