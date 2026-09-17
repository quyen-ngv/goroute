package com.ds.goroute.mapper;

import com.ds.goroute.entity.RefreshToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface RefreshTokenMapper {
    int insert(RefreshToken token);

    RefreshToken selectByToken(@Param("token") String token);

    /**
     * Matches either the stored digest of a token or, for rows written before refresh
     * tokens were hashed, the raw value. The digest is preferred when both exist.
     */
    RefreshToken selectByAnyToken(@Param("hashed") String hashed, @Param("raw") String raw);

    RefreshToken selectById(@Param("id") UUID id);

    int deleteByToken(@Param("token") String token);

    int deleteByAnyToken(@Param("hashed") String hashed, @Param("raw") String raw);

    int deleteById(@Param("id") UUID id);

    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * Brings the expiry forward; a row that already expires earlier is left alone.
     */
    int expireById(@Param("id") UUID id, @Param("expiresAt") LocalDateTime expiresAt);
}
