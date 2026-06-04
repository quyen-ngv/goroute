package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserDevice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserDeviceMapper {

    void insert(UserDevice device);

    UserDevice findById(@Param("id") UUID id);

    List<UserDevice> findActiveByUserId(@Param("userId") UUID userId);

    UserDevice findByUserIdAndToken(@Param("userId") UUID userId, @Param("fcmToken") String fcmToken);

    void updateToken(@Param("id") UUID id, @Param("fcmToken") String fcmToken);

    void updateDevice(@Param("id") UUID id,
                      @Param("userId") UUID userId,
                      @Param("fcmToken") String fcmToken,
                      @Param("language") String language,
                      @Param("isActive") Boolean isActive);

    void deactivate(@Param("id") UUID id);

    void deleteByToken(@Param("fcmToken") String fcmToken);

    void deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);
}
