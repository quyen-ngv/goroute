package com.ds.goroute.service;

import com.ds.goroute.dto.request.ChangePasswordRequest;
import com.ds.goroute.dto.response.TemporaryPasswordResponse;
import com.ds.goroute.entity.User;

import java.util.UUID;

public interface UserAccountService {
    TemporaryPasswordResponse resetPassword(UUID userId, String requestedTemporaryPassword);
    void changePassword(UUID userId, ChangePasswordRequest request);
    ProvisionedAccount provision(String username, String email, String fullName, String requestedTemporaryPassword);

    record ProvisionedAccount(User user, String temporaryPassword) {}
}
