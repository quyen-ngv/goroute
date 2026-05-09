package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AppleLoginRequest;
import com.ds.goroute.dto.request.GoogleLoginRequest;
import com.ds.goroute.dto.request.LoginRequest;
import com.ds.goroute.dto.request.RefreshTokenRequest;
import com.ds.goroute.dto.request.RegisterRequest;
import com.ds.goroute.dto.response.AuthResponse;
import com.ds.goroute.dto.response.UserResponse;
import com.ds.goroute.entity.RefreshToken;
import com.ds.goroute.entity.TripMember;
import com.ds.goroute.entity.ExpenseSplit;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.RefreshTokenRepository;
import com.ds.goroute.repository.TripMemberRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.AuthService;
import com.ds.goroute.thirdparty.apple.AppleTokenInfo;
import com.ds.goroute.thirdparty.apple.AppleTokenVerifier;
import com.ds.goroute.thirdparty.google.GoogleTokenInfo;
import com.ds.goroute.thirdparty.google.GoogleTokenVerifier;
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
    private final ExpenseSplitRepository expenseSplitRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;

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
        // Verify Google ID token with Firebase
        GoogleTokenInfo tokenInfo = googleTokenVerifier.verify(request.getIdToken());

        // Find user by Google ID or email
        User user = userRepository.findByProviderId(tokenInfo.getSub())
                .orElseGet(() -> {
                    // Check if email already exists (link Google to existing account)
                    var existingUser = userRepository.findByEmail(tokenInfo.getEmail());
                    if (existingUser.isPresent()) {
                        User existing = existingUser.get();
                        existing.setProviderId(tokenInfo.getSub());
                        existing.setProvider(AuthProvider.GOOGLE);
                        userRepository.update(existing);
                        log.info("Linked Google account to existing user: {}", tokenInfo.getEmail());
                        return existing;
                    }

                    // Check if email exists but was soft deleted
                    var deletedUser = userRepository.findByEmailIncludingDeleted(tokenInfo.getEmail());
                    if (deletedUser.isPresent()) {
                        User existing = deletedUser.get();
                        existing.setProviderId(tokenInfo.getSub());
                        existing.setProvider(AuthProvider.GOOGLE);
                        existing.setDeletedAt(null); // Restore user
                        existing.setFullName(tokenInfo.getName());
                        existing.setAvatarUrl(tokenInfo.getPicture());
                        userRepository.update(existing);
                        log.info("Restored deleted user via Google: {}", tokenInfo.getEmail());
                        return existing;
                    }

                    // Create new user
                    String username = generateUsername(tokenInfo.getEmail());
                    User newUser = User.builder()
                            .id(UUID.randomUUID())
                            .username(username)
                            .provider(AuthProvider.GOOGLE)
                            .providerId(tokenInfo.getSub())
                            .email(tokenInfo.getEmail())
                            .fullName(tokenInfo.getName())
                            .avatarUrl(tokenInfo.getPicture())
                            .defaultCurrency("VND")
                            .defaultTravelMode("driving")
                            .language("vi")
                            .theme("system")
                            .onboardingCompleted(false)
                            .build();
                    userRepository.insert(newUser);
                    log.info("New user created via Google: {}", tokenInfo.getEmail());

                    // Auto-link guest members
                    autoLinkGuestMembers(newUser);

                    return newUser;
                });

        // Merge guest data if provided
        if (request.getGuestId() != null) {
            mergeGuestDataToUser(request.getGuestId(), user.getId());
        }

        return generateAuthResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse appleLogin(AppleLoginRequest request) {
        // Verify Apple identity token
        AppleTokenInfo tokenInfo = appleTokenVerifier.verify(request.getIdentityToken());

        // Validate Apple user ID matches token
        if (!tokenInfo.getSub().equals(request.getAppleUserId())) {
            throw new BusinessException(ErrorConstant.UNAUTHORIZED, "Invalid Apple user ID");
        }

        // Find user by Apple ID or create new
        User user = userRepository.findByProviderId(tokenInfo.getSub())
                .orElseGet(() -> {
                    // Check if email already exists (link Apple to existing account)
                    var existingUser = userRepository.findByEmail(tokenInfo.getEmail());
                    if (existingUser.isPresent()) {
                        User existing = existingUser.get();
                        existing.setProviderId(tokenInfo.getSub());
                        existing.setProvider(AuthProvider.APPLE);
                        userRepository.update(existing);
                        log.info("Linked Apple account to existing user: {}", tokenInfo.getEmail());
                        return existing;
                    }

                    // Check if email exists but was soft deleted
                    var deletedUser = userRepository.findByEmailIncludingDeleted(tokenInfo.getEmail());
                    if (deletedUser.isPresent()) {
                        User existing = deletedUser.get();
                        existing.setProviderId(tokenInfo.getSub());
                        existing.setProvider(AuthProvider.APPLE);
                        existing.setDeletedAt(null); // Restore user
                        userRepository.update(existing);
                        log.info("Restored deleted user via Apple: {}", tokenInfo.getEmail());
                        return existing;
                    }

                    String username = generateUsername(tokenInfo.getEmail());
                    User newUser = User.builder()
                            .id(UUID.randomUUID())
                            .username(username)
                            .provider(AuthProvider.APPLE)
                            .providerId(tokenInfo.getSub())
                            .email(tokenInfo.getEmail())
                            .defaultCurrency("VND")
                            .defaultTravelMode("driving")
                            .language("vi")
                            .theme("system")
                            .onboardingCompleted(false)
                            .build();
                    userRepository.insert(newUser);
                    log.info("New user created via Apple: {}", tokenInfo.getEmail());

                    // Auto-link guest members
                    autoLinkGuestMembers(newUser);

                    return newUser;
                });

        // Merge guest data if provided
        if (request.getGuestId() != null) {
            mergeGuestDataToUser(request.getGuestId(), user.getId());
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
            // Check if user is already a member of this trip as a different member (not this guest)
            var existingMember = tripMemberRepository.findByTripIdAndUserId(guestMember.getTripId(), user.getId());
            if (existingMember.isPresent() && !existingMember.get().getId().equals(guestMember.getId())) {
                log.warn("User {} already member of trip {} (different from guest), skipping guest link",
                        user.getId(), guestMember.getTripId());
                continue;
            }

            // Update all expense splits for this guest member
            List<ExpenseSplit> guestSplits = expenseSplitRepository.findByGuestMemberId(guestMember.getId());
            log.info("Found {} expense splits for guest member {}", guestSplits.size(), guestMember.getId());

            for (ExpenseSplit split : guestSplits) {
                split.setUserId(user.getId());
                split.setGuestMemberId(null);
                split.setGuestName(null); // Clear guest name since now linked to real user
                expenseSplitRepository.update(split);
            }

            // Link guest to real user
            guestMember.setUserId(user.getId());
            guestMember.setIsGuest(false);
            guestMember.setStatus(MemberStatus.ACCEPTED);
            tripMemberRepository.updateById(guestMember);

            log.info("Linked guest member {} to user {} in trip {}, updated {} expense splits",
                    guestMember.getId(), user.getId(), guestMember.getTripId(), guestSplits.size());
        }
    }

    private void mergeGuestDataToUser(String guestId, UUID userId) {
        // TODO: Implement guest data merge logic
        // This should transfer guest's trips, activities, expenses to real user
        log.info("Merging guest data from {} to user {}", guestId, userId);
    }
}