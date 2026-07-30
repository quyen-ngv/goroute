package com.ds.goroute.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PortalSessionResponse {
    private UserResponse user;
    private boolean admin;
    private boolean partner;
    private boolean mustChangePassword;
    private List<String> roles;
    private List<String> permissions;
}
