package com.ds.goroute.service.impl;

import com.ds.goroute.config.LoginLockoutProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AdminLoginRequest;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.HostOrganizationService;
import com.ds.goroute.service.LoginAttemptGuard;
import com.ds.goroute.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Console password login locks an account after repeated wrong passwords (epic 01). */
class AdminAuthServiceImplLockoutTest {

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final LoginLockoutProperties properties = new LoginLockoutProperties();
    private final User user = User.builder().id(UUID.randomUUID()).username("owner").passwordHash("hash").build();

    private AdminAuthServiceImpl service;

    @BeforeEach
    void setUp() {
        properties.setMaxFailures(3);
        service = new AdminAuthServiceImpl(users, mock(AdminMapper.class), encoder, mock(JwtUtils.class),
                mock(HostOrganizationService.class), new LoginAttemptGuard(properties));
        when(users.findByUsername("owner")).thenReturn(Optional.of(user));
        when(encoder.matches(eq("wrong"), anyString())).thenReturn(false);
    }

    @Test
    void locksAfterTheConfiguredNumberOfWrongPasswords() {
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.login(request("wrong")))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getError().getCode())
                    .isEqualTo(ErrorConstant.UNAUTHORIZED);
        }

        assertThatThrownBy(() -> service.login(request("right")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getError().getCode())
                .isEqualTo(ErrorConstant.LOGIN_TEMPORARILY_LOCKED);
        verify(encoder, never()).matches(eq("right"), anyString());
    }

    @Test
    void aCorrectPasswordClearsTheCount() {
        when(encoder.matches(eq("right"), anyString())).thenReturn(true);
        assertThatThrownBy(() -> service.login(request("wrong"))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.login(request("wrong"))).isInstanceOf(BusinessException.class);
        try {
            service.login(request("right"));
        } catch (BusinessException ignored) {
            // No portal role on this mock user; the password itself was accepted.
        }

        assertThatThrownBy(() -> service.login(request("wrong")))
                .extracting(e -> ((BusinessException) e).getError().getCode())
                .isEqualTo(ErrorConstant.UNAUTHORIZED);
    }

    @Test
    void doesNothingWhenDisabled() {
        properties.setEnabled(false);
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(request("wrong")))
                    .extracting(e -> ((BusinessException) e).getError().getCode())
                    .isEqualTo(ErrorConstant.UNAUTHORIZED);
        }
    }

    private static AdminLoginRequest request(String password) {
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("owner");
        request.setPassword(password);
        return request;
    }
}
