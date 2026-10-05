package com.ds.goroute.localevent;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.AdminApiKey;
import com.ds.goroute.localevent.dto.AdminApiKeyCreateRequest;
import com.ds.goroute.localevent.dto.AdminApiKeyCreatedResponse;
import com.ds.goroute.localevent.persistence.AdminApiKeyRepository;
import com.ds.goroute.localevent.service.AdminApiKeyService;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminApiKeyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 4, 3, 0);

    private final AdminApiKeyRepository repository = mock(AdminApiKeyRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AdminMapper adminMapper = mock(AdminMapper.class);
    private final AdminApiKeyService service =
            new AdminApiKeyService(repository, users, adminMapper, new ObjectMapper().findAndRegisterModules());
    private final UUID owner = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        Field clock = AdminApiKeyService.class.getDeclaredField("clock");
        clock.setAccessible(true);
        clock.set(service, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        when(users.findById(owner)).thenReturn(Optional.of(User.builder().id(owner).accountStatus("ACTIVE").build()));
        when(adminMapper.hasAnyRole(owner)).thenReturn(true);
    }

    @Test
    void aNewKeyIsShownOnceAndOnlyItsHashIsStored() {
        AdminApiKeyCreatedResponse created = service.create(
                AdminApiKeyCreateRequest.builder().name("Crawler sự kiện").build(), owner);

        assertThat(created.getKey()).matches("grk_[0-9A-Za-z]{40}");
        assertThat(created.getKeyPrefix()).isEqualTo(created.getKey().substring(0, 12));
        assertThat(created.getScopes()).containsExactly("events:import");
        assertThat(created.getOwnerUserId()).isEqualTo(owner);
        ArgumentCaptor<AdminApiKey> stored = ArgumentCaptor.forClass(AdminApiKey.class);
        verify(repository).insert(stored.capture());
        assertThat(stored.getValue().getKeyHash()).isEqualTo(AdminApiKeyService.hash(created.getKey())).hasSize(64);
        assertThat(stored.getValue().toString()).doesNotContain(created.getKey());
    }

    @Test
    void theOwnerMustBeAnAdmin() {
        UUID notAdmin = UUID.randomUUID();
        when(users.findById(notAdmin)).thenReturn(Optional.of(User.builder().id(notAdmin).build()));

        assertThatThrownBy(() -> service.create(
                AdminApiKeyCreateRequest.builder().name("x").ownerUserId(notAdmin).build(), owner))
                .isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getError().getCode())
                        .isEqualTo(ErrorConstant.ADMIN_API_KEY_OWNER_NOT_ADMIN));
    }

    @Test
    void aValidKeyIsLookedUpByItsHashAndItsUseRecorded() {
        String key = "grk_" + "b".repeat(40);
        UUID keyId = UUID.randomUUID();
        when(repository.findByHash(AdminApiKeyService.hash(key))).thenReturn(Optional.of(
                AdminApiKey.builder().id(keyId).ownerUserId(owner).build()));

        Optional<AdminApiKeyService.AuthenticatedKey> result = service.authenticate(key);

        assertThat(result).contains(new AdminApiKeyService.AuthenticatedKey(keyId, owner));
        verify(repository).touchLastUsed(keyId, NOW, NOW.minusMinutes(1));
    }

    @Test
    void revokedExpiredOrOrphanedKeysAreRefused() {
        String revoked = "grk_" + "c".repeat(40);
        String expired = "grk_" + "d".repeat(40);
        String orphaned = "grk_" + "e".repeat(40);
        UUID gone = UUID.randomUUID();
        when(repository.findByHash(AdminApiKeyService.hash(revoked))).thenReturn(Optional.of(
                AdminApiKey.builder().id(UUID.randomUUID()).ownerUserId(owner).revokedAt(NOW.minusDays(1)).build()));
        when(repository.findByHash(AdminApiKeyService.hash(expired))).thenReturn(Optional.of(
                AdminApiKey.builder().id(UUID.randomUUID()).ownerUserId(owner).expiresAt(NOW).build()));
        when(repository.findByHash(AdminApiKeyService.hash(orphaned))).thenReturn(Optional.of(
                AdminApiKey.builder().id(UUID.randomUUID()).ownerUserId(gone).build()));
        when(users.findById(gone)).thenReturn(Optional.empty());

        assertThat(service.authenticate(revoked)).isEmpty();
        assertThat(service.authenticate(expired)).isEmpty();
        assertThat(service.authenticate(orphaned)).isEmpty();
        verify(repository, never()).touchLastUsed(any(), any(), any());
    }

    @Test
    void malformedKeysNeverReachTheDatabase() {
        assertThat(service.authenticate("not-a-key")).isEmpty();
        assertThat(service.authenticate("grk_short")).isEmpty();
        verify(repository, never()).findByHash(anyString());
    }

    @Test
    void revokingIsIdempotent() {
        UUID keyId = UUID.randomUUID();
        when(repository.findById(keyId)).thenReturn(Optional.of(
                AdminApiKey.builder().id(keyId).ownerUserId(owner).revokedAt(NOW).build()));

        assertThat(service.revoke(keyId).getStatus()).isEqualTo("REVOKED");
        verify(repository).revoke(eq(keyId), any());
    }
}
