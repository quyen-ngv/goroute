package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AppleLoginRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.RefreshTokenRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.thirdparty.apple.AppleTokenInfo;
import com.ds.goroute.thirdparty.apple.AppleTokenVerifier;
import com.ds.goroute.thirdparty.google.GoogleTokenVerifier;
import com.ds.goroute.utils.JwtUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplAppleSignupTest {

    private static final String APPLE_SUB = "001234.abcdef.0123";
    private static final String IDENTITY_TOKEN = "header.payload.signature";

    private final UserRepository users = mock(UserRepository.class);
    private final TripMemberRepository tripMembers = mock(TripMemberRepository.class);
    private final AppleTokenVerifier appleVerifier = mock(AppleTokenVerifier.class);
    private final AnimalUsernameGenerator animalNames = mock(AnimalUsernameGenerator.class);
    private final JwtUtils jwt = mock(JwtUtils.class);

    private final AuthServiceImpl service = new AuthServiceImpl(
            users, mock(com.ds.goroute.service.LoginAttemptGuard.class), mock(RefreshTokenRepository.class), tripMembers, mock(ExpenseSplitRepository.class),
            mock(ExpenseRepository.class), jwt, mock(PasswordEncoder.class),
            mock(GoogleTokenVerifier.class), appleVerifier, animalNames);

    @BeforeEach
    void setUp() {
        when(users.findByProviderId(APPLE_SUB)).thenReturn(Optional.empty());
        when(users.findByEmail(anyString())).thenReturn(Optional.empty());
        when(users.findByEmailIncludingDeleted(anyString())).thenReturn(Optional.empty());
        when(jwt.generateToken(anyMap(), anyString())).thenReturn("access");
        when(animalNames.generate()).thenReturn("BravePanda4821");
    }

    @Test
    void hiddenEmailGetsTheSameAnimalNameForUsernameAndFullName() {
        signIn("x7kq2pd9zt@privaterelay.appleid.com");

        User created = insertedUser();
        assertEquals("BravePanda4821", created.getUsername());
        assertEquals("BravePanda4821", created.getFullName());
        assertEquals("x7kq2pd9zt@privaterelay.appleid.com", created.getEmail());
    }

    @Test
    void missingEmailClaimIsRefusedInsteadOfInsertingANullEmail() {
        BusinessException refused = assertThrows(BusinessException.class, () -> signIn(null));

        assertEquals(ErrorConstant.APPLE_EMAIL_REQUIRED, refused.getError().getCode());
        verify(users, never()).insert(any());
        verify(users, never()).findByEmail(any());
    }

    @Test
    void blankEmailClaimIsRefusedToo() {
        BusinessException refused = assertThrows(BusinessException.class, () -> signIn("  "));

        assertEquals(ErrorConstant.APPLE_EMAIL_REQUIRED, refused.getError().getCode());
        verify(users, never()).insert(any());
    }

    @Test
    void existingAppleAccountStillSignsInWithoutAnEmailClaim() {
        User existing = User.builder()
                .id(UUID.randomUUID())
                .username("minh")
                .email("minh@icloud.com")
                .providerId(APPLE_SUB)
                .build();
        when(users.findByProviderId(APPLE_SUB)).thenReturn(Optional.of(existing));

        AuthResponse response = signIn(null);

        assertEquals("minh@icloud.com", response.getUser().getEmail());
        verify(users, never()).insert(any());
    }

    @Test
    void sharedEmailKeepsTheEmailDerivedUsername() {
        when(users.existsByUsername("minh.tran")).thenReturn(false);

        signIn("Minh.Tran@icloud.com");

        User created = insertedUser();
        assertEquals("minh.tran", created.getUsername());
        assertEquals("minh.tran", created.getFullName());
        verify(animalNames, never()).generate();
    }

    @Test
    void sharedEmailUsernameSkipsOneHeldByASoftDeletedAccount() {
        when(users.existsByUsername("minh")).thenReturn(true);
        when(users.existsByUsername("minh1")).thenReturn(false);

        signIn("minh@icloud.com");

        assertEquals("minh1", insertedUser().getUsername());
    }

    @Test
    void sharedNameBecomesTheFullNameWhileHiddenEmailStillGetsAnAnimalUsername() {
        signIn("x7kq2pd9zt@privaterelay.appleid.com", "  Minh Trần ");

        User created = insertedUser();
        assertEquals("BravePanda4821", created.getUsername());
        assertEquals("Minh Trần", created.getFullName());
    }

    @Test
    void blankNameFallsBackToTheUsername() {
        signIn("x7kq2pd9zt@privaterelay.appleid.com", "   ");

        assertEquals("BravePanda4821", insertedUser().getFullName());
    }

    private AuthResponse signIn(String email) {
        return signIn(email, null);
    }

    private AuthResponse signIn(String email, String fullName) {
        when(appleVerifier.verify(IDENTITY_TOKEN)).thenReturn(AppleTokenInfo.builder()
                .sub(APPLE_SUB)
                .email(email)
                .emailVerified(true)
                .build());
        return service.appleLogin(AppleLoginRequest.builder()
                .identityToken(IDENTITY_TOKEN)
                .appleUserId(APPLE_SUB)
                .fullName(fullName)
                .build());
    }

    private User insertedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(users).insert(captor.capture());
        return captor.getValue();
    }
}
