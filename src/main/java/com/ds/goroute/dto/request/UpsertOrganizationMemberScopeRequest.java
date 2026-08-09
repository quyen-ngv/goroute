package com.ds.goroute.dto.request;
import com.ds.goroute.type.OrganizationResourceType;import com.ds.goroute.type.PartnerRole;import com.ds.goroute.type.ScopeAccessEffect;
import jakarta.validation.constraints.*;import lombok.Data;import java.time.LocalDateTime;import java.util.*;
@Data public class UpsertOrganizationMemberScopeRequest {
    @NotNull private OrganizationResourceType resourceType;
    private UUID resourceId;
    private PartnerRole roleCode;
    private ScopeAccessEffect accessEffect=ScopeAccessEffect.ALLOW;
    private List<String> permissions;
    private LocalDateTime validFrom;
    private LocalDateTime validUntil;
}
