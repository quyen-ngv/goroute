package com.ds.goroute.dto.request;
import jakarta.validation.constraints.*;import lombok.Data;import java.time.LocalDateTime;import java.util.*;
@Data public class UpsertOrganizationMemberScopeRequest {
    @NotBlank @Pattern(regexp="HOTEL|ACTIVITY") private String resourceType;
    private UUID resourceId;
    @Pattern(regexp="PARTNER_ADMIN|PROPERTY_MANAGER|REVENUE_MANAGER|RESERVATION_AGENT|FRONT_DESK|HOUSEKEEPING|FINANCE|CONTENT_MANAGER|TOUR_OPERATOR|GUIDE|TICKET_SCANNER|VIEWER")
    private String roleCode;
    @Pattern(regexp="ALLOW|DENY") private String accessEffect="ALLOW";
    private List<String> permissions;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}
