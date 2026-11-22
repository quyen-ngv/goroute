package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.AdminApiKey;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Admin API keys (resources/mapper/AdminApiKeyMapper.xml). */
@Mapper
public interface AdminApiKeyMapper {

    int insert(AdminApiKey key);

    AdminApiKey findById(@Param("id") UUID id);

    AdminApiKey findByHash(@Param("keyHash") String keyHash);

    List<AdminApiKey> findAll(@Param("limit") int limit, @Param("offset") int offset);

    long countAll();

    /** Idempotent: an already revoked key keeps its first revocation time. */
    int revoke(@Param("id") UUID id, @Param("revokedAt") LocalDateTime revokedAt);

    /** Records use at most once per minute: only writes when last_used_at is older than {@code staleBefore}. */
    int touchLastUsed(@Param("id") UUID id,
                      @Param("usedAt") LocalDateTime usedAt,
                      @Param("staleBefore") LocalDateTime staleBefore);
}
