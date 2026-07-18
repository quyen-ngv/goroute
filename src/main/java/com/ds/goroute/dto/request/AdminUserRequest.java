package com.ds.goroute.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class AdminUserRequest {
    @NotBlank private String username;
    @NotBlank private String email;
    @NotBlank private String fullName;
    private String password;
    private Set<String> roles;
}
