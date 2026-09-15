package com.ds.goroute.service;

import com.ds.goroute.dto.response.AdminRoleResponse;
import com.ds.goroute.dto.response.AdminUserRolesResponse;

import java.util.List;
import java.util.UUID;

public interface AdminRoleManagementService {
    List<AdminRoleResponse> listAllRoles();
    List<AdminUserRolesResponse> listAdminUsers(String search);
    List<AdminUserRolesResponse> searchAllUsers(String search);
    AdminUserRolesResponse getUserRoles(UUID userId);
    AdminUserRolesResponse assignRoles(UUID userId, List<String> roleCodes);
    AdminUserRolesResponse removeRole(UUID userId, String roleCode);
    void removeAllRoles(UUID userId);
}
