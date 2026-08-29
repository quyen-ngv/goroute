package com.ds.goroute.dto.response;

import com.ds.goroute.type.TrustRole;
import com.ds.goroute.type.TrustRoleStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TrustRoleResponse {
    private UUID id;
    private UUID userId;
    private String userDisplayName;
    private TrustRole role;
    private String areaProvinceCode;
    private String areaProvinceName;
    private TrustRoleStatus status;
    private String applicationNote;
    private String decisionNote;
    private LocalDateTime decidedAt;
    private LocalDateTime grantedAt;
    private LocalDateTime reviewDueAt;
    private LocalDateTime createdAt;
}
