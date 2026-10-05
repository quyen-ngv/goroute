package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.AdminApiKey;
import com.ds.goroute.localevent.dto.AdminApiKeyCreateRequest;
import com.ds.goroute.localevent.dto.AdminApiKeyCreatedResponse;
import com.ds.goroute.localevent.dto.AdminApiKeyResponse;
import com.ds.goroute.localevent.persistence.AdminApiKeyRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.AdminApiKeyScope;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-key machine credentials for the admin API (today: the events crawler). A key is
 * {@code grk_} + 40 random base62 characters, shown once; only its SHA-256 is stored. A key acts
 * as its owner — an admin user — so RBAC and {@code created_by} attribution work unchanged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminApiKeyService {

    public static final String KEY_PREFIX = "grk_";
    static final int RANDOM_LENGTH = 40;
    /** "grk_" plus the first 8 random characters: recognisable in a list, useless to call with. */
    static final int DISPLAY_PREFIX_LENGTH = KEY_PREFIX.length() + 8;
    /** last_used_at is written at most once per this interval. */
    static final Duration LAST_USED_RESOLUTION = Duration.ofMinutes(1);
    private static final String BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final AdminApiKeyRepository repository;
    private final UserRepository userRepository;
    private final AdminMapper adminMapper;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    Clock clock = Clock.systemUTC();

    /** Who a valid key acts as. */
    public record AuthenticatedKey(UUID keyId, UUID ownerUserId) {
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminApiKeyResponse> list(int page, int size) {
        LocalDateTime now = LocalDateTime.now(clock);
        List<AdminApiKeyResponse> items = repository.findAll(size, page * size).stream()
                .map(key -> toResponse(key, now))
                .toList();
        return PageResponse.of(items, repository.countAll(), page, size);
    }

    @Transactional
    public AdminApiKeyCreatedResponse create(AdminApiKeyCreateRequest request, UUID actorUserId) {
        UUID ownerUserId = request.getOwnerUserId() == null ? actorUserId : request.getOwnerUserId();
        if (!isActiveAdmin(ownerUserId)) {
            throw new BusinessException(ErrorConstant.ADMIN_API_KEY_OWNER_NOT_ADMIN,
                    "The key owner must be an active administrator");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime expiresAt = request.getExpiresAt() == null
                ? null
                : LocalDateTime.ofInstant(request.getExpiresAt().toInstant(), ZoneOffset.UTC);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "expiresAt must be in the future");
        }
        String key = generateKey();
        List<String> scopes = List.of(AdminApiKeyScope.EVENTS_IMPORT.code());
        AdminApiKey row = AdminApiKey.builder()
                .id(UUID.randomUUID())
                .name(request.getName().trim())
                .keyPrefix(key.substring(0, DISPLAY_PREFIX_LENGTH))
                .keyHash(hash(key))
                .ownerUserId(ownerUserId)
                .scopes(json(scopes))
                .createdBy(actorUserId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
        repository.insert(row);
        return AdminApiKeyCreatedResponse.builder()
                .id(row.getId())
                .name(row.getName())
                .keyPrefix(row.getKeyPrefix())
                .key(key)
                .scopes(scopes)
                .ownerUserId(ownerUserId)
                .createdAt(now)
                .expiresAt(expiresAt)
                .build();
    }

    /** Idempotent: revoking a revoked key keeps the first revocation time. */
    @Transactional
    public AdminApiKeyResponse revoke(UUID keyId) {
        repository.findById(keyId).orElseThrow(AdminApiKeyService::notFound);
        LocalDateTime now = LocalDateTime.now(clock);
        repository.revoke(keyId, now);
        return toResponse(repository.findById(keyId).orElseThrow(AdminApiKeyService::notFound), now);
    }

    /**
     * Resolves a presented key. Empty when it is unknown, revoked, expired, or its owner is
     * deleted, locked/disabled or no longer an admin. Records the use at most once a minute.
     */
    public Optional<AuthenticatedKey> authenticate(String presentedKey) {
        if (presentedKey == null || !presentedKey.startsWith(KEY_PREFIX)
                || presentedKey.length() != KEY_PREFIX.length() + RANDOM_LENGTH) {
            return Optional.empty();
        }
        Optional<AdminApiKey> found = repository.findByHash(hash(presentedKey));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AdminApiKey key = found.get();
        LocalDateTime now = LocalDateTime.now(clock);
        if (key.getRevokedAt() != null) {
            return Optional.empty();
        }
        if (key.getExpiresAt() != null && !key.getExpiresAt().isAfter(now)) {
            return Optional.empty();
        }
        if (!isActiveAdmin(key.getOwnerUserId())) {
            return Optional.empty();
        }
        try {
            repository.touchLastUsed(key.getId(), now, now.minus(LAST_USED_RESOLUTION));
        } catch (RuntimeException exception) {
            // Bookkeeping only; never refuse a valid key because the timestamp could not be written.
            log.warn("Could not record admin API key use: {}", exception.getClass().getSimpleName());
        }
        return Optional.of(new AuthenticatedKey(key.getId(), key.getOwnerUserId()));
    }

    /** Lower-case hex SHA-256, the form stored in {@code admin_api_keys.key_hash}. */
    public static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    String generateKey() {
        StringBuilder key = new StringBuilder(KEY_PREFIX);
        for (int index = 0; index < RANDOM_LENGTH; index++) {
            key.append(BASE62.charAt(secureRandom.nextInt(BASE62.length())));
        }
        return key.toString();
    }

    private boolean isActiveAdmin(UUID userId) {
        if (userId == null) {
            return false;
        }
        Optional<User> user = userRepository.findById(userId);
        if (user.isEmpty() || user.get().getDeletedAt() != null) {
            return false;
        }
        String status = user.get().getAccountStatus();
        if (status != null && !"ACTIVE".equals(status)) {
            return false;
        }
        return adminMapper.hasAnyRole(userId);
    }

    private AdminApiKeyResponse toResponse(AdminApiKey key, LocalDateTime now) {
        String status = key.getRevokedAt() != null ? "REVOKED"
                : key.getExpiresAt() != null && !key.getExpiresAt().isAfter(now) ? "EXPIRED"
                : "ACTIVE";
        return AdminApiKeyResponse.builder()
                .id(key.getId())
                .name(key.getName())
                .keyPrefix(key.getKeyPrefix())
                .scopes(scopes(key.getScopes()))
                .ownerUserId(key.getOwnerUserId())
                .ownerName(key.getOwnerName())
                .createdBy(key.getCreatedBy())
                .createdAt(key.getCreatedAt())
                .expiresAt(key.getExpiresAt())
                .lastUsedAt(key.getLastUsedAt())
                .revokedAt(key.getRevokedAt())
                .status(status)
                .build();
    }

    private List<String> scopes(String json) {
        try {
            return json == null ? List.of() : objectMapper.readValue(json, STRING_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not serialise scopes", exception);
        }
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.ADMIN_API_KEY_NOT_FOUND, "API key not found");
    }
}
