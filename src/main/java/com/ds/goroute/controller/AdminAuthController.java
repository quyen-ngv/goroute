package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.service.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {
    private final AdminAuthService service;

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@Valid @RequestBody AdminLoginRequest request) {
        return ResponseEntity.ok(BaseResponse.ofSucceeded(service.login(request)));
    }
}
