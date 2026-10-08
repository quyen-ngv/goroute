package com.ds.goroute.mapper;

import com.ds.goroute.entity.IdempotencyRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface IdempotencyRecordMapper {

    /** Claims the key. 1 when this call created the row, 0 when the key is already held. */
    int insertIfAbsent(IdempotencyRecord record);

    IdempotencyRecord findByUserAndKey(@Param("userId") UUID userId, @Param("idempotencyKey") String idempotencyKey);

    /** Takes over an IN_PROGRESS row whose lock is older than {@code staleBefore}. */
    int takeOverStale(@Param("id") UUID id,
                      @Param("staleBefore") LocalDateTime staleBefore,
                      @Param("now") LocalDateTime now);

    int complete(@Param("id") UUID id,
                 @Param("responseStatus") int responseStatus,
                 @Param("responseContentType") String responseContentType,
                 @Param("responseBody") byte[] responseBody,
                 @Param("now") LocalDateTime now);

    int deleteById(@Param("id") UUID id);

    /** Deletes this one row only if it has expired, so a live claim is never removed. */
    int deleteIfExpired(@Param("id") UUID id, @Param("now") LocalDateTime now);

    /** Deletes up to {@code limit} expired rows. */
    int deleteExpired(@Param("now") LocalDateTime now, @Param("limit") int limit);
}
