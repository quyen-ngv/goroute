package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateUserCheckinRequest;
import com.ds.goroute.dto.request.HandOverManagedAccountRequest;
import com.ds.goroute.dto.request.ManagedAccountRequest;
import com.ds.goroute.dto.request.UpdateUserCheckinRequest;
import com.ds.goroute.dto.response.CheckinVideoUploadResponse;
import com.ds.goroute.dto.response.ManagedAccountResponse;
import com.ds.goroute.dto.response.ManagedCheckinMediaResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.dto.response.UserCheckinResponse;
import com.ds.goroute.entity.ManagedAccountRow;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ManagedAccountService;
import com.ds.goroute.service.UserCheckinService;
import com.ds.goroute.service.UserGuideService;
import com.ds.goroute.type.AuthProvider;
import com.ds.goroute.type.CheckinMediaType;
import com.ds.goroute.type.ManagedAccountStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Managed accounts (epic 20).
 *
 * <p>An ordinary HUMAN account with a random password nobody is told and a placeholder email on a
 * reserved domain ({@code .invalid} cannot receive mail), so nobody can sign in to it until the
 * handover gives it a real email. While users.managed_by_admin is true the console may post for it;
 * after the handover every "act as" call answers ACCOUNT_NOT_MANAGED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagedAccountServiceImpl implements ManagedAccountService {

    /** The placeholder email's domain. RFC 2606 reserves .invalid, so no mail ever goes out. */
    static final String PLACEHOLDER_EMAIL_DOMAIN = "@managed.ondetour.invalid";

    /** The badge a new managed account gets when the console names none. */
    static final String DEFAULT_GUIDE_TITLE = "Local Guide";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int RANDOM_PASSWORD_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserGuideService userGuideService;
    private final UserCheckinService checkinService;
    private final FileUploadService fileUploadService;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public SlicePageResponse<ManagedAccountResponse> list(String search, ManagedAccountStatus status,
                                                         int page, int size) {
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        String trimmedSearch = search == null || search.isBlank() ? null : search.trim();
        List<ManagedAccountRow> fetched = userRepository.findManagedAccounts(trimmedSearch,
                status == null ? ManagedAccountStatus.MANAGED : status, limit + 1, safePage * limit);
        boolean hasMore = fetched.size() > limit;
        List<ManagedAccountRow> rows = hasMore ? fetched.subList(0, limit) : fetched;
        return SlicePageResponse.<ManagedAccountResponse>builder()
                .items(rows.stream().map(ManagedAccountServiceImpl::toResponse).toList())
                .page(safePage)
                .size(limit)
                .hasMore(hasMore)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ManagedAccountResponse get(UUID userId) {
        return load(userId);
    }

    @Override
    @Transactional
    public ManagedAccountResponse create(UUID adminId, ManagedAccountRequest request) {
        String username = request.getUsername().trim();
        // Soft-deleted rows count: the UNIQUE constraint on users.username does.
        if (userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This username is already taken");
        }
        String email = placeholderEmail(username);
        if (userRepository.findByEmailIncludingDeleted(email).isPresent()) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This username is already taken");
        }
        UUID userId = UUID.randomUUID();
        userRepository.insert(User.builder()
                .id(userId)
                .username(username)
                .email(email)
                .fullName(request.getFullName().trim())
                .bio(blankToNull(request.getBio()))
                .avatarUrl(blankToNull(request.getAvatarUrl()))
                // Never shown and never sent anywhere: the account is unusable until the handover.
                .passwordHash(passwordEncoder.encode(randomPassword()))
                .provider(AuthProvider.LOCAL)
                .defaultCurrency("VND")
                .defaultTravelMode("driving")
                .language("vi")
                .theme("system")
                .onboardingCompleted(false)
                .build());
        userRepository.markManaged(userId);

        // null: the default badge; "": no badge.
        String guideTitle = request.getGuideTitle() == null ? DEFAULT_GUIDE_TITLE : request.getGuideTitle().trim();
        if (!guideTitle.isEmpty()) {
            userGuideService.grant(userId, guideTitle, "Managed account", null, adminId);
        }
        log.info("Managed account {} ({}) created by admin {}", userId, username, adminId);
        return load(userId);
    }

    @Override
    @Transactional
    public ManagedAccountResponse update(UUID adminId, UUID userId, ManagedAccountRequest request) {
        User user = requireManaged(userId);
        String username = request.getUsername().trim();
        boolean isRenamed = !username.equals(user.getUsername());
        if (isRenamed && userRepository.existsByUsername(username)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This username is already taken");
        }
        // The placeholder follows the username, so the old one is free for a later account.
        String email = isRenamed && user.getEmail() != null && user.getEmail().endsWith(PLACEHOLDER_EMAIL_DOMAIN)
                ? placeholderEmail(username)
                : user.getEmail();
        if (userRepository.updateManagedProfile(userId, request.getFullName().trim(), username, email,
                blankToNull(request.getBio()), blankToNull(request.getAvatarUrl())) != 1) {
            throw notManaged();
        }

        // null leaves the badge alone; "" revokes it; anything else (re)grants it with that title.
        if (request.getGuideTitle() != null) {
            String guideTitle = request.getGuideTitle().trim();
            if (guideTitle.isEmpty()) {
                if (userGuideService.isGuide(userId)) {
                    userGuideService.revoke(userId, "Removed from the managed account", adminId);
                }
            } else {
                userGuideService.grant(userId, guideTitle, "Managed account", null, adminId);
            }
        }
        return load(userId);
    }

    @Override
    @Transactional
    public ManagedAccountResponse handOver(UUID adminId, UUID userId, HandOverManagedAccountRequest request) {
        requireManaged(userId);
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        Optional<User> holder = userRepository.findByEmailIncludingDeleted(email);
        if (holder.isPresent() && !holder.get().getId().equals(userId)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This email is already used by another account");
        }
        String passwordHash = request.getPassword() == null || request.getPassword().isBlank()
                ? null
                : passwordEncoder.encode(request.getPassword());
        if (userRepository.handOverManagedAccount(userId, email, passwordHash, adminId) != 1) {
            throw notManaged();
        }
        log.info("Managed account {} handed over by admin {}", userId, adminId);
        return load(userId);
    }

    @Override
    @Transactional
    public UserCheckinResponse createCheckin(UUID userId, CreateUserCheckinRequest request) {
        requireManaged(userId);
        // The user's own use case: review, Stars, passport and follower fan-out run as for anyone.
        return checkinService.create(userId, request);
    }

    @Override
    @Transactional
    public UserCheckinResponse updateCheckin(UUID userId, UUID checkinId, UpdateUserCheckinRequest request) {
        requireManaged(userId);
        return checkinService.update(userId, checkinId, request);
    }

    @Override
    @Transactional
    public void deleteCheckin(UUID userId, UUID checkinId) {
        requireManaged(userId);
        checkinService.delete(userId, checkinId, false);
    }

    /**
     * A video goes through the check-in video upload of that account, so it lands under
     * checkin-videos/{userId}/ where the check-in rule looks for it; anything else is stored like an
     * ordinary image upload of that account (also the poster of a video). Not transactional: the
     * upload must not hold a database connection.
     */
    @Override
    public ManagedCheckinMediaResponse uploadCheckinMedia(UUID userId, MultipartFile file) {
        requireManaged(userId);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A file is required");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (contentType.startsWith("video/")) {
            CheckinVideoUploadResponse video = checkinService.uploadVideo(userId, file);
            return ManagedCheckinMediaResponse.builder()
                    .url(video.getUrl())
                    .mediaType(CheckinMediaType.VIDEO)
                    .durationSeconds(video.getDurationSeconds())
                    .build();
        }
        return ManagedCheckinMediaResponse.builder()
                .url(fileUploadService.uploadImage(userId, file))
                .mediaType(CheckinMediaType.IMAGE)
                .build();
    }

    private User requireManaged(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));
        if (!Boolean.TRUE.equals(user.getManagedByAdmin())) {
            throw notManaged();
        }
        return user;
    }

    private ManagedAccountResponse load(UUID userId) {
        return userRepository.findManagedAccount(userId)
                .map(ManagedAccountServiceImpl::toResponse)
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "Managed account not found"));
    }

    private static ManagedAccountResponse toResponse(ManagedAccountRow row) {
        return ManagedAccountResponse.builder()
                .id(row.getId())
                .fullName(row.getFullName())
                .username(row.getUsername())
                .email(row.getEmail())
                .avatarUrl(row.getAvatarUrl())
                .bio(row.getBio())
                .guide(Boolean.TRUE.equals(row.getGuide()))
                .guideTitle(row.getGuideTitle())
                .managedSince(row.getManagedSince())
                .handedOverAt(row.getHandedOverAt())
                .postCount(row.getPostCount() == null ? 0 : row.getPostCount())
                .checkinCount(row.getCheckinCount() == null ? 0 : row.getCheckinCount())
                .followerCount(row.getFollowerCount() == null ? 0 : row.getFollowerCount())
                .build();
    }

    private static BusinessException notManaged() {
        return new BusinessException(ErrorConstant.ACCOUNT_NOT_MANAGED, "This account is no longer managed by the team");
    }

    static String placeholderEmail(String username) {
        return username.toLowerCase(Locale.ROOT) + PLACEHOLDER_EMAIL_DOMAIN;
    }

    private static String randomPassword() {
        byte[] bytes = new byte[RANDOM_PASSWORD_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
