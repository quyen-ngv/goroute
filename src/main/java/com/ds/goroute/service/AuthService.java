package com.ds.goroute.service;

import com.ds.goroute.dto.request.AppleLoginRequest;
import com.ds.goroute.dto.request.GoogleLoginRequest;
import com.ds.goroute.dto.request.LoginRequest;
import com.ds.goroute.dto.request.RefreshTokenRequest;
import com.ds.goroute.dto.request.RegisterRequest;
import com.ds.goroute.dto.response.AuthResponse;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    
    AuthResponse login(LoginRequest request);
    
    AuthResponse googleLogin(GoogleLoginRequest request);
    
    AuthResponse appleLogin(AppleLoginRequest request);
    
    AuthResponse refreshToken(RefreshTokenRequest request);
    
    void logout(String refreshToken);
}
