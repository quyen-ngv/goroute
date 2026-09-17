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
import com.ds.goroute.repository.ExpenseRepository;
import com.ds.goroute.repository.ExpenseSplitRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.AuthService;
import com.ds.goroute.thirdparty.apple.AppleTokenInfo;
import com.ds.goroute.thirdparty.apple.AppleTokenVerifier;
import com.ds.goroute.thirdparty.google.GoogleTokenInfo;
import com.ds.goroute.thirdparty.google.GoogleTokenVerifier;
import com.ds.goroute.type.AuthProvider;
import com.ds.goroute.type.MemberStatus;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.JwtUtils;
import com.fasterxml.jackson.core.type.TypeReference;
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
    private final ExpenseRepository expenseRepository;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final AppleTokenVerifier appleTokenVerifier;

    private static final long REFRESH_TOKEN_EXPIRY = 2592000000L; // 30 days in ms

    /**
     * How long a refresh token stays usable after it has been exchanged. It is deliberately
     * generous rather than tight: the client only learns the replacement token from the
     * response body, so a refresh that commits here and then loses its response on a flaky
     * mobile connection leaves the device holding the old token. A day of overlap means
     * that device recovers on its next attempt instead of being signed out, while a stolen
     * token still dies a day after the real owner next refreshes rather than lasting the
     * full thirty.
     */
    private static final long ROTATION_GRACE_SECONDS = 86400L;

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
        /*
         * Firebase issues one token shape for every provider it fronts, so the same endpoint
         * also carries an Apple sign-in coming from the web console. Record who actually
         * signed the person in; storing that as GOOGLE would make the account unrecognisable
         * to the native Apple path, which looks the user up by provider id.
         */
        AuthProvider provider = "apple.com".equals(tokenInfo.getSignInProvider())
                ? AuthProvider.APPLE
                : AuthProvider.GOOGLE;

        // Find user by Google ID or email
        User user = userRepository.findByProviderId(tokenInfo.getSub())
                .orElseGet(() -> {
                    // Check if email already exists (link Google to existing account)
                    var existingUser = userRepository.findByEmail(tokenInfo.getEmail());
                    if (existingUser.isPresent()) {
                        requireVerifiedEmailForLinking(tokenInfo);
                        User existing = existingUser.get();
                        existing.setProviderId(tokenInfo.getSub());
                        existing.setProvider(provider);
                        userRepository.update(existing);
                        log.info("Linked {} account to existing user: {}", provider, tokenInfo.getEmail());
                        return existing;
                    }

                    // Check if email exists but was soft deleted
                    var deletedUser = userRepository.findByEmailIncludingDeleted(tokenInfo.getEmail());
                    if (deletedUser.isPresent()) {
                        requireVerifiedEmailForLinking(tokenInfo);
                        User existing = deletedUser.get();
                        existing.setProviderId(tokenInfo.getSub());
                        existing.setProvider(provider);
                        existing.setDeletedAt(null); // Restore user
                        // Same reason as the insert below: a terse provider must not blank a NOT NULL column.
                        if (tokenInfo.getName() != null && !tokenInfo.getName().isBlank()) {
                            existing.setFullName(tokenInfo.getName());
                        }
                        existing.setAvatarUrl(tokenInfo.getPicture());
                        userRepository.update(existing);
                        log.info("Restored deleted user via {}: {}", provider, tokenInfo.getEmail());
                        return existing;
                    }

                    // Create new user
                    String username = generateUsername(tokenInfo.getEmail());
                    User newUser = User.builder()
                            .id(UUID.randomUUID())
                            .username(username)
                            .provider(provider)
                            .providerId(tokenInfo.getSub())
                            .email(tokenInfo.getEmail())
                            /*
                             * Google always sends a display name; Apple only sends one on the very
                             * first authorization and Firebase forwards nothing afterwards. The
                             * column is NOT NULL, so fall back to the generated username rather
                             * than failing the insert on a provider that is allowed to be terse.
                             */
                            .fullName(tokenInfo.getName() == null || tokenInfo.getName().isBlank()
                                    ? username : tokenInfo.getName())
                            .avatarUrl(tokenInfo.getPicture())
                            .defaultCurrency("VND")
                            .defaultTravelMode("driving")
                            .language("vi")
                            .theme("system")
                            .onboardingCompleted(false)
                            .build();
                    userRepository.insert(newUser);
                    log.info("New user created via {}: {}", provider, tokenInfo.getEmail());

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
                        requireVerifiedEmailForLinking(tokenInfo);
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
                        requireVerifiedEmailForLinking(tokenInfo);
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
                            .fullName(username)
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

        AuthResponse response = generateAuthResponse(user);

        // Rotate: the presented token stops being a 30-day credential now that a new one
        // has been handed out. It is not deleted outright because the app refreshes from
        // two independent places (the Dio interceptor and the realtime client) and both
        // may present the same token within milliseconds of each other; a short grace
        // window lets the loser of that race succeed instead of being logged out.
        refreshTokenRepository.expireById(
                refreshToken.getId(), LocalDateTime.now().plusSeconds(ROTATION_GRACE_SECONDS));

        return response;
    }

    @Override
    @Transactional
    public void logout(String refreshToken) {
        refreshTokenRepository.deleteByToken(refreshToken);
        log.info("User logged out");
    }

    private AuthResponse generateAuthResponse(User user) {
        // Generate access token (1 day)
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId().toString());
        claims.put("email", user.getEmail());
        String accessToken = jwtUtils.generateToken(claims, user.getId().toString());

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
                .bio(user.getBio())
                .socialLinks(parseSocialLinks(user.getSocialLinks()))
                .defaultCurrency(user.getDefaultCurrency())
                .defaultTravelMode(user.getDefaultTravelMode())
                .language(user.getLanguage())
                .theme(user.getTheme())
                .onboardingCompleted(user.getOnboardingCompleted())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .user(userResponse)
                .build();
    }

    /**
     * Linking a social identity onto an account that already holds the same email hands
     * the token holder that account, so the provider has to stand behind the email.
     * {@code GoogleTokenInfo.emailVerified} is false only when the provider explicitly
     * said the address is unverified — an absent claim leaves it true, so logins that
     * work today keep working. Creating a brand new account is untouched: there is no
     * existing account to take over.
     */
    /** Apple half of the same rule; see the Google overload above. */
    private void requireVerifiedEmailForLinking(AppleTokenInfo tokenInfo) {
        if (!tokenInfo.isEmailVerified()) {
            log.warn("Refused to link Apple identity to existing account: provider reports the address unverified");
            throw new BusinessException(ErrorConstant.UNAUTHORIZED,
                    "Apple account email is not verified");
        }
    }

    private void requireVerifiedEmailForLinking(GoogleTokenInfo tokenInfo) {
        if (!tokenInfo.isEmailVerified()) {
            log.warn("Refused to link Google identity to existing account: provider reports {} unverified",
                    tokenInfo.getEmail());
            throw new BusinessException(ErrorConstant.UNAUTHORIZED,
                    "Google account email is not verified");
        }
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

    private Map<String, String> parseSocialLinks(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return Map.of();
        }
        Map<String, String> links = JsonUtils.fromJson(rawJson, new TypeReference<Map<String, String>>() {});
        return links == null ? Map.of() : links;
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

            // Expenses this guest paid keep both paid_by and paid_by_guest_member_id set
            // to the trip_members id, and the wallet only counts an expense as paid by a
            // user when paid_by_guest_member_id IS NULL. Without this the money the guest
            // laid out stayed invisible to the account they were just linked to, the same
            // way the splits above would have. Same person, same email, same member row.
            int movedExpenses = expenseRepository.reassignGuestPayerToUser(guestMember.getId(), user.getId());

            // Link guest to real user
            guestMember.setUserId(user.getId());
            guestMember.setIsGuest(false);
            guestMember.setStatus(MemberStatus.ACCEPTED);
            tripMemberRepository.updateById(guestMember);

            log.info("Linked guest member {} to user {} in trip {}, updated {} expense splits"
                            + " and {} paid expenses",
                    guestMember.getId(), user.getId(), guestMember.getTripId(), guestSplits.size(),
                    movedExpenses);
        }
    }

    private void mergeGuestDataToUser(String guestId, UUID userId) {
        // TODO: Implement guest data merge logic
        // This should transfer guest's trips, activities, expenses to real user
        log.info("Merging guest data from {} to user {}", guestId, userId);
    }
}
