package com.ds.goroute.controller;

import com.ds.goroute.dto.request.AppleLoginRequest;
import com.ds.goroute.dto.request.GoogleLoginRequest;
import com.ds.goroute.dto.request.LoginRequest;
import com.ds.goroute.dto.request.RefreshTokenRequest;
import com.ds.goroute.dto.request.RegisterRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.service.AuthService;
import com.ds.goroute.dto.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController extends BaseController {
    
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<BaseResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ofSucceeded(response));
    }

    @PostMapping("/login")
    public ResponseEntity<BaseResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    /**
     * Any Firebase-issued ID token, not only Google's. The mobile app signs in with Google
     * through Firebase and the operator console signs in with Google <em>or</em> Apple through
     * the Firebase Web SDK; all three produce the same token shape, and the service reads
     * {@code firebase.sign_in_provider} to record which provider it really was. The native iOS
     * Sign in with Apple flow does not come through here — it sends Apple's own token to
     * {@code /apple}, which is verified against Apple's keys instead.
     */
    @PostMapping("/google")
    public ResponseEntity<BaseResponse<AuthResponse>> googleLogin(@Valid @RequestBody GoogleLoginRequest request) {
        AuthResponse response = authService.googleLogin(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/apple")
    public ResponseEntity<BaseResponse<AuthResponse>> appleLogin(@Valid @RequestBody AppleLoginRequest request) {
        AuthResponse response = authService.appleLogin(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<BaseResponse<AuthResponse>> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(ofSucceeded(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<BaseResponse<Void>> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.ok(ofSucceeded(null));
    }
}
