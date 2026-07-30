package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ChangePasswordRequest;
import com.ds.goroute.dto.response.TemporaryPasswordResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.UserAccountService;
import com.ds.goroute.type.AuthProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccountServiceImpl implements UserAccountService {
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final SecureRandom random = new SecureRandom();

    @Override
    @Transactional
    public ProvisionedAccount provision(String username, String email, String fullName, String requestedPassword) {
        if (blank(username) || blank(email) || blank(fullName)) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "Owner username, email and full name are required");
        }
        String password = blank(requestedPassword) ? generatePassword() : requestedPassword;
        if (password.length() < 10) throw new BusinessException(ErrorConstant.BAD_REQUEST, "Temporary password must contain at least 10 characters");
        User user = User.builder().id(UUID.randomUUID()).username(username.trim()).email(email.trim().toLowerCase())
                .fullName(fullName.trim()).passwordHash(encoder.encode(password)).provider(AuthProvider.LOCAL)
                .defaultCurrency("VND").defaultTravelMode("driving").language("vi").theme("system")
                .onboardingCompleted(false).mustChangePassword(true).accountStatus("ACTIVE").build();
        try { users.insert(user); }
        catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Username or email already exists");
        }
        return new ProvisionedAccount(user, password);
    }

    @Override
    @Transactional
    public TemporaryPasswordResponse resetPassword(UUID userId, String requestedPassword) {
        users.findById(userId).orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
        String password = blank(requestedPassword) ? generatePassword() : requestedPassword;
        if (password.length() < 10) throw new BusinessException(ErrorConstant.BAD_REQUEST, "Temporary password must contain at least 10 characters");
        if (users.updatePassword(userId, encoder.encode(password), true, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND);
        }
        return new TemporaryPasswordResponse(userId, password, true);
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND));
        if (user.getPasswordHash() == null || !encoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Current password is incorrect");
        }
        if (encoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorConstant.BAD_REQUEST, "New password must be different from the current password");
        }
        if (users.updatePassword(userId, encoder.encode(request.getNewPassword()), false, LocalDateTime.now()) != 1) {
            throw new BusinessException(ErrorConstant.USER_NOT_FOUND);
        }
    }

    private String generatePassword() {
        StringBuilder value = new StringBuilder(16);
        for (int i = 0; i < 16; i++) value.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return value.toString();
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
