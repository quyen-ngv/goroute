package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserDeviceMapper {

    void insert(UserDevice device);

    /**
     * Registers a token for one person, taking it over from whoever held it before.
     *
     * <p>One statement on the unique token, so two accounts registering the same phone at the
     * same moment end with one owner rather than a constraint error.
     */
    UserDevice upsertByToken(UserDevice device);

    UserDevice findById(@Param("id") UUID id);

    List<UserDevice> findActiveByUserId(@Param("userId") UUID userId);

    UserDevice findByUserIdAndToken(@Param("userId") UUID userId, @Param("fcmToken") String fcmToken);

    void updateToken(@Param("id") UUID id, @Param("fcmToken") String fcmToken);

    int updateDevice(@Param("id") UUID id,
                      @Param("userId") UUID userId,
                      @Param("fcmToken") String fcmToken,
                      @Param("language") String language,
                      @Param("isActive") Boolean isActive);

    void deactivate(@Param("id") UUID id);

    void deleteByToken(@Param("fcmToken") String fcmToken);

    /** Frees a token for {@code deviceId}: any other row holding it is a stale registration. */
    int deleteByTokenExceptDevice(@Param("fcmToken") String fcmToken, @Param("deviceId") UUID deviceId);

    int deleteByUserId(@Param("userId") UUID userId);

    int deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
