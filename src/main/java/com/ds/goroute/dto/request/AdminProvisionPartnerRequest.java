package com.ds.goroute.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class AdminProvisionPartnerRequest {
    @NotNull @Valid private CreateHostOrganizationRequest organization;
    private UUID ownerUserId;
    @Valid private OwnerAccount ownerAccount;

    @Data
    public static class OwnerAccount {
        @Size(max = 100) private String username;
        @Email @Size(max = 320) private String email;
        @Size(max = 200) private String fullName;
        @Size(min = 10, max = 100) private String temporaryPassword;
    }
}
