package com.ds.goroute.config;

import com.ds.goroute.mapper.AdminMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("adminAuthorization")
public class AdminAuthorization {
    private final AdminMapper adminMapper;

    public AdminAuthorization(AdminMapper adminMapper) { this.adminMapper = adminMapper; }

    public boolean can(Authentication authentication, String resource, String action) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        try {
            UUID userId = UUID.fromString(authentication.getName());
            return adminMapper.hasPermission(userId, resource, action);
        } catch (Exception ignored) { return false; }
    }

    public boolean owns(Authentication authentication, String resource, UUID resourceId) {
        if (authentication == null || resourceId == null) return false;
        try {
            return adminMapper.ownsResource(UUID.fromString(authentication.getName()), resource, resourceId);
        } catch (Exception ignored) { return false; }
    }
}
