package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AssignRolesRequest;
import com.ds.goroute.dto.response.AdminRoleResponse;
import com.ds.goroute.dto.response.AdminUserRolesResponse;
import com.ds.goroute.service.AdminRoleManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/role-management")
@RequiredArgsConstructor
public class AdminRoleManagementController extends BaseController {

    private final AdminRoleManagementService roleManagementService;

    @GetMapping("/roles")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','get')")
    public ResponseEntity<BaseResponse<List<AdminRoleResponse>>> listRoles() {
        return ResponseEntity.ok(ofSucceeded(roleManagementService.listAllRoles()));
    }

    @GetMapping("/search-users")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','get')")
    public ResponseEntity<BaseResponse<List<AdminUserRolesResponse>>> searchAllUsers(
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ofSucceeded(roleManagementService.searchAllUsers(search)));
    }

    @GetMapping("/users")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','get')")
    public ResponseEntity<BaseResponse<List<AdminUserRolesResponse>>> listAdminUsers(
            @RequestParam(required = false) String search
    ) {
        return ResponseEntity.ok(ofSucceeded(roleManagementService.listAdminUsers(search)));
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','get')")
    public ResponseEntity<BaseResponse<AdminUserRolesResponse>> getUserRoles(
            @PathVariable UUID userId
    ) {
        return ResponseEntity.ok(ofSucceeded(roleManagementService.getUserRoles(userId)));
    }

    @PostMapping("/users/{userId}/roles")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','update')")
    public ResponseEntity<BaseResponse<AdminUserRolesResponse>> assignRoles(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRolesRequest request
    ) {
        return ResponseEntity.ok(ofSucceeded(roleManagementService.assignRoles(userId, request.getRoleCodes())));
    }

    @DeleteMapping("/users/{userId}/roles/{roleCode}")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','update')")
    public ResponseEntity<BaseResponse<AdminUserRolesResponse>> removeRole(
            @PathVariable UUID userId,
            @PathVariable String roleCode
    ) {
        return ResponseEntity.ok(ofSucceeded(roleManagementService.removeRole(userId, roleCode)));
    }

    @DeleteMapping("/users/{userId}/roles")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','update')")
    public ResponseEntity<BaseResponse<Void>> removeAllRoles(
            @PathVariable UUID userId
    ) {
        roleManagementService.removeAllRoles(userId);
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
