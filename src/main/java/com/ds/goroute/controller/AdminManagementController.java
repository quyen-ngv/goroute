package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AdminUserRequest;
import com.ds.goroute.dto.request.ResetPasswordRequest;
import com.ds.goroute.dto.response.TemporaryPasswordResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.AuthProvider;
import com.ds.goroute.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.*;

@RestController
@RequestMapping("/v1/api/admin")
@RequiredArgsConstructor
public class AdminManagementController {
    private final AdminMapper adminMapper;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final UserAccountService userAccountService;

    @GetMapping("/users")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','get')")
    public BaseResponse<List<Map<String,Object>>> users(@RequestParam(defaultValue="") String search,
                                                        @RequestParam(defaultValue="0") int page,
                                                        @RequestParam(defaultValue="20") int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        return BaseResponse.ofSucceeded(adminMapper.findUsers(search, safeSize, Math.max(page, 0) * safeSize));
    }

    @GetMapping("/dashboard")
    public BaseResponse<Map<String, Object>> dashboard() {
        Map<String, Object> result = new LinkedHashMap<>(adminMapper.findDashboardStats());
        result.put("daily", adminMapper.findDashboardDailyStats());
        result.put("topPlaces", adminMapper.findTopSelectedPlaces());
        result.put("topCities", adminMapper.findTopDestinations());
        return BaseResponse.ofSucceeded(result);
    }

    @GetMapping("/roles")
    @PreAuthorize("@adminAuthorization.can(authentication,'roles','get')")
    public BaseResponse<List<Map<String,Object>>> roles() {
        return BaseResponse.ofSucceeded(adminMapper.findRoles());
    }

    @GetMapping("/users/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','get')")
    public BaseResponse<Map<String, Object>> userDetail(@PathVariable UUID id) {
        Map<String, Object> result = adminMapper.findUserDetail(id);
        result.put("trips", adminMapper.findUserTrips(id));
        result.put("contributions", adminMapper.findUserContributions(id));
        result.put("media", adminMapper.findUserMedia(id));
        return BaseResponse.ofSucceeded(result);
    }

    @PostMapping("/users")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','create')")
    public BaseResponse<Map<String,Object>> createUser(@Valid @RequestBody AdminUserRequest request) {
        UUID id = UUID.randomUUID();
        User user = User.builder().id(id).username(request.getUsername()).email(request.getEmail()).fullName(request.getFullName())
                .passwordHash(encoder.encode(request.getPassword())).provider(AuthProvider.LOCAL).defaultCurrency("VND")
                .defaultTravelMode("driving").language("vi").theme("system").onboardingCompleted(false).build();
        users.insert(user);
        assignRoleCodes(id, request.getRoles());
        return BaseResponse.ofSucceeded(Map.of("id", id, "username", request.getUsername()));
    }

    @PutMapping("/users/{id}/roles")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','update')")
    public BaseResponse<Void> assignRoles(@PathVariable UUID id, @RequestBody Set<String> roles) {
        adminMapper.deleteUserRoles(id);
        assignRoleCodes(id, roles);
        return BaseResponse.ofSucceeded(null);
    }

    private void assignRoleCodes(UUID userId, Set<String> roles) {
        if (roles == null || roles.isEmpty()) return;
        adminMapper.insertUserRoles(userId, roles);
    }

    @DeleteMapping("/users/{id}")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','delete')")
    public BaseResponse<Void> deleteUser(@PathVariable UUID id) {
        users.softDeleteById(id);
        return BaseResponse.ofSucceeded(null);
    }

    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("@adminAuthorization.can(authentication,'users','update')")
    public BaseResponse<TemporaryPasswordResponse> resetPassword(@PathVariable UUID id,
            @Valid @RequestBody ResetPasswordRequest request) {
        return BaseResponse.ofSucceeded(userAccountService.resetPassword(id, request.getTemporaryPassword()));
    }
}
