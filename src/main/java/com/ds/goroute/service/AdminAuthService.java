package com.ds.goroute.service;

import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.dto.response.AuthResponse;

public interface AdminAuthService { AuthResponse login(AdminLoginRequest request); }
