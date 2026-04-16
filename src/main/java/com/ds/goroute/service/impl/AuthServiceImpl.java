package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.GoogleLoginRequest;
import com.ds.goroute.dto.request.LoginRequest;
import com.ds.goroute.dto.request.RefreshTokenRequest;
import com.ds.goroute.dto.request.RegisterRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.RefreshToken;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.RefreshTokenRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.AuthService;
import com.ds.goroute.type.AuthProvider;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TripMemberRepository tripMemberRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;

    private static final long REFRESH_TOKEN_EXPIRY = 2592000000L; // 30 days in ms

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        var existingUser = userRepository.findByEmail(request.getEmail());
        if (existingUser.isPresent()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Email already exists");
        }

        // Generate username from email (before @)
        String username = generateUsername(request.getEmail());

        // Create new user
        User user = User.builder()
                .id(UUID.randomUUID())
                .email(request.getEmail())
                .username(username)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .provider(AuthProvider.LOCAL)
                .defaultCurrency("VND")
                .defaultTravelMode("driving")
                .language("vi")
                .theme("system")
                .onboardingCompleted(false)
                .build();

        userRepository.insert(user);
        log.info("User registered: {}", user.getEmail());

        // Auto-link guest members with matching email
        autoLinkGuestMembers(user);

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // Try to find user by email or username
        User user = userRepository.findByEmail(request.getEmail())
                .or(() -> userRepository.findByUsername(request.getEmail()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid email/username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid email/username or password");
        }

        log.info("User logged in: {}", user.getEmail());
        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse googleLogin(GoogleLoginRequest request) {
        // TODO: Verify Google ID token with Google API
        // For now, we'll assume the token is valid

        // Extract user info from token (in real implementation, verify with Google)
        String providerId = "google_" + UUID.randomUUID().toString();

        var userOpt = userRepository.findByEmail(providerId); // TODO: Add findByProviderId method
        User user;
        if (userOpt.isEmpty()) {
            // Create new user from Google
            user = User.builder()
                    .id(UUID.randomUUID())
                    .provider(AuthProvider.GOOGLE)
                    .providerId(providerId)
                    .defaultCurrency("VND")
                    .defaultTravelMode("driving")
                    .language("vi")
                    .theme("system")
                    .onboardingCompleted(false)
                    .build();
            userRepository.insert(user);
            log.info("New user created via Google: {}", providerId);
        } else {
            user = userOpt.get();
        }

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenRepository.findByToken(request.getRefreshToken())
                .orElseThrow(() -> new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid or expired refresh token"));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid or expired refresh token");
        }

        User user = userRepository.findById(refreshToken.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "User not found"));

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
        log.info("User logged out");
    }

    private AuthResponse generateAuthResponse(User user) {
        // Generate access token (15 minutes)
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        String accessToken = jwtUtils.generateToken(claims, user.getId().toString(), 900000L);

        // Generate refresh token (30 days)
        RefreshToken refreshToken = RefreshToken.builder()
                .id(UUID.randomUUID())
                .userId(user.getId())
                .token(UUID.randomUUID().toString())
                .expiresAt(LocalDateTime.now().plusDays(30))
                .build();
        refreshTokenRepository.insert(refreshToken);

        UserResponse userResponse = UserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .defaultCurrency(user.getDefaultCurrency())
                .language(user.getLanguage())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .user(userResponse)
                .build();
    }

    private String generateUsername(String email) {
        // Extract username from email (before @)
        String baseUsername = email.split("@")[0].toLowerCase();

        // Check if username exists
        var existingUser = userRepository.findByUsername(baseUsername);
        if (existingUser.isEmpty()) {
            return baseUsername;
        }

        // If exists, append random number
        int suffix = 1;
        String username = baseUsername + suffix;
        while (userRepository.findByUsername(username).isPresent()) {
            suffix++;
            username = baseUsername + suffix;
        }
        return username;
    }

    private void autoLinkGuestMembers(User user) {
        // Find all guest members with matching email
        List<TripMember> guestMembers = tripMemberRepository.findGuestsByEmail(user.getEmail());
        
        if (guestMembers.isEmpty()) {
            return;
        }

        log.info("Auto-linking {} guest member(s) to user: {}", guestMembers.size(), user.getEmail());
        
        for (TripMember guestMember : guestMembers) {
            // Check if user is already a member of this trip
            var existingMember = tripMemberRepository.findByTripIdAndUserId(guestMember.getTripId(), user.getId());
            if (existingMember.isPresent()) {
                log.warn("User {} already member of trip {}, skipping guest link", user.getId(), guestMember.getTripId());
                continue;
            }

            // Link guest to real user
            guestMember.setUserId(user.getId());
            guestMember.setIsGuest(false);
            guestMember.setStatus(MemberStatus.ACCEPTED);
            tripMemberRepository.updateById(guestMember);
            
            log.info("Linked guest member {} to user {} in trip {}", 
                guestMember.getId(), user.getId(), guestMember.getTripId());
        }
    }
}