package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.request.ChangePasswordRequest;
import com.ds.goroute.service.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v1/api/account")
@RequiredArgsConstructor
public class AccountSecurityController {
    private final UserAccountService service;

    @PutMapping("/password")
    public BaseResponse<Void> changePassword(Authentication authentication,
                                             @Valid @RequestBody ChangePasswordRequest request) {
        if (authentication == null) throw new AuthenticationCredentialsNotFoundException("Authentication required");
        service.changePassword(UUID.fromString(authentication.getName()), request);
        return BaseResponse.ofSucceeded();
    }
}
