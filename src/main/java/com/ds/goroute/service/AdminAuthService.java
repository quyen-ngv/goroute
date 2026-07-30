package com.ds.goroute.service;

import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.dto.response.PortalSessionResponse;

import java.util.UUID;

public interface AdminAuthService {
    AuthResponse login(AdminLoginRequest request);
    PortalSessionResponse session(UUID userId);
}
