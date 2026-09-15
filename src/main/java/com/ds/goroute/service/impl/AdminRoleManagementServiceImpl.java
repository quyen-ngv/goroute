package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.AdminRoleResponse;
import com.ds.goroute.dto.response.AdminUserRolesResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.mapper.AdminRoleManagementMapper;
import com.ds.goroute.service.AdminRoleManagementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminRoleManagementServiceImpl implements AdminRoleManagementService {

    private final AdminRoleManagementMapper roleManagementMapper;
    private final AdminMapper adminMapper;

    @Override
    public List<AdminRoleResponse> listAllRoles() {
        List<Map<String, Object>> roles = roleManagementMapper.findAllRoles();
        return roles.stream().map(this::mapToRoleResponse).collect(Collectors.toList());
    }

    @Override
    public List<AdminUserRolesResponse> listAdminUsers(String search) {
        List<Map<String, Object>> users = roleManagementMapper.findAllAdminUsers(search);
        return users.stream().map(this::mapToUserRolesResponse).collect(Collectors.toList());
    }

    @Override
    public List<AdminUserRolesResponse> searchAllUsers(String search) {
        List<Map<String, Object>> users = roleManagementMapper.searchAllUsers(search);
        return users.stream().map(this::mapToUserRolesResponseSimple).collect(Collectors.toList());
    }

    @Override
    public AdminUserRolesResponse getUserRoles(UUID userId) {
        Map<String, Object> user = roleManagementMapper.findUserWithRoles(userId);
        if (user == null) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND);
        }
        return mapToUserRolesResponse(user);
    }

    @Override
    @Transactional
    public AdminUserRolesResponse assignRoles(UUID userId, List<String> roleCodes) {
        // Validate user exists
        Map<String, Object> user = roleManagementMapper.findUserWithRoles(userId);
        if (user == null) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND);
        }

        // Validate role codes
        List<String> validRoles = roleManagementMapper.findAllRoles().stream()
                .map(r -> (String) r.get("code"))
                .collect(Collectors.toList());
        
        List<String> invalidRoles = roleCodes.stream()
                .filter(code -> !validRoles.contains(code))
                .collect(Collectors.toList());
        
        if (!invalidRoles.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, 
                "Invalid role codes: " + String.join(", ", invalidRoles));
        }

        // Clear existing roles and insert new ones
        adminMapper.deleteUserRoles(userId);
        if (!roleCodes.isEmpty()) {
            adminMapper.insertUserRoles(userId, new HashSet<>(roleCodes));
        }

        log.info("Assigned roles {} to user {}", roleCodes, userId);
        return getUserRoles(userId);
    }

    @Override
    @Transactional
    public AdminUserRolesResponse removeRole(UUID userId, String roleCode) {
        roleManagementMapper.deleteUserRole(userId, roleCode);
        log.info("Removed role {} from user {}", roleCode, userId);
        return getUserRoles(userId);
    }

    @Override
    @Transactional
    public void removeAllRoles(UUID userId) {
        adminMapper.deleteUserRoles(userId);
        log.info("Removed all admin roles from user {}", userId);
    }

    private AdminRoleResponse mapToRoleResponse(Map<String, Object> data) {
        // PostgreSQL returns String[] for array_agg, not List
        Object resourcesObj = data.get("resources");
        List<String> resourcesList = Collections.emptyList();
        if (resourcesObj instanceof String[]) {
            resourcesList = Arrays.asList((String[]) resourcesObj);
        } else if (resourcesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> temp = (List<String>) resourcesObj;
            resourcesList = temp;
        }
        
        // Parse permission_details JSON
        Object permDetailsObj = data.get("permission_details");
        Map<String, List<String>> permissionsMap = new LinkedHashMap<>();
        if (permDetailsObj != null) {
            try {
                String permDetailsJson = permDetailsObj.toString();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> permissionsList = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(permDetailsJson, List.class);
                permissionsMap = permissionsList.stream()
                        .collect(Collectors.groupingBy(
                                p -> (String) p.get("resource"),
                                LinkedHashMap::new,
                                Collectors.mapping(p -> (String) p.get("action"), Collectors.toList())
                        ));
            } catch (Exception e) {
                log.warn("Failed to parse permission_details JSON: {}", permDetailsObj, e);
            }
        }

        return AdminRoleResponse.builder()
                .code((String) data.get("code"))
                .name((String) data.get("name"))
                .permissionCount(((Number) data.getOrDefault("permission_count", 0)).intValue())
                .resources(resourcesList)
                .permissions(permissionsMap)
                .build();
    }

    private AdminUserRolesResponse mapToUserRolesResponse(Map<String, Object> data) {
        // Parse roles JSON
        Object rolesObj = data.get("roles");
        List<AdminUserRolesResponse.RoleInfo> roles = Collections.emptyList();
        if (rolesObj != null) {
            try {
                String rolesJson = rolesObj.toString();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> rolesList = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(rolesJson, List.class);
                roles = rolesList.stream()
                        .map(r -> AdminUserRolesResponse.RoleInfo.builder()
                                .code((String) r.get("code"))
                                .name((String) r.get("name"))
                                .build())
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("Failed to parse roles JSON: {}", rolesObj, e);
            }
        }

        // Parse permissions JSON
        Object permissionsObj = data.get("permissions");
        Map<String, List<String>> allPermissions = new LinkedHashMap<>();
        if (permissionsObj != null) {
            try {
                String permissionsJson = permissionsObj.toString();
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> permissionsList = new com.fasterxml.jackson.databind.ObjectMapper()
                        .readValue(permissionsJson, List.class);
                allPermissions = permissionsList.stream()
                        .collect(Collectors.groupingBy(
                                p -> (String) p.get("resource"),
                                LinkedHashMap::new,
                                Collectors.mapping(p -> (String) p.get("action"), Collectors.toList())
                        ));
            } catch (Exception e) {
                log.warn("Failed to parse permissions JSON: {}", permissionsObj, e);
            }
        }

        return AdminUserRolesResponse.builder()
                .userId(UUID.fromString((String) data.get("user_id")))
                .username((String) data.get("username"))
                .email((String) data.get("email"))
                .fullName((String) data.get("full_name"))
                .roles(roles)
                .allPermissions(allPermissions)
                .build();
    }

    private AdminUserRolesResponse mapToUserRolesResponseSimple(Map<String, Object> data) {
        return AdminUserRolesResponse.builder()
                .userId(UUID.fromString((String) data.get("user_id")))
                .username((String) data.get("username"))
                .email((String) data.get("email"))
                .fullName((String) data.get("full_name"))
                .roles(Collections.emptyList())
                .allPermissions(Collections.emptyMap())
                .build();
    }
}
