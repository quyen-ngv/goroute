package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserRolesResponse {
    private UUID userId;
    private String username;
    private String email;
    private String fullName;
    private List<RoleInfo> roles;
    private Map<String, List<String>> allPermissions; // resource -> [actions]
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RoleInfo {
        private String code;
        private String name;
    }
}
