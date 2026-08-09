package com.ds.goroute.dto.request;

import com.ds.goroute.type.PartnerRole;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ProvisionPartnerMemberRequest {
    @NotBlank @Size(max = 100) private String username;
    @NotBlank @Email @Size(max = 320) private String email;
    @NotBlank @Size(max = 200) private String fullName;
    @Size(min = 10, max = 100) private String temporaryPassword;
    @NotNull private PartnerRole roleCode;
    private List<String> permissions;
}
