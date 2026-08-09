package com.ds.goroute.dto.request;

import com.ds.goroute.type.OrganizationMemberStatus;
import com.ds.goroute.type.PartnerRole;

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
    private PartnerRole roleCode;
    private OrganizationMemberStatus memberStatus = OrganizationMemberStatus.ACTIVE;
    private List<String> permissions;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}
