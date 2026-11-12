package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserConsent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

@Mapper
public interface UserConsentMapper {

    int insert(UserConsent consent);

    /** The newest row of each purpose the person has answered. */
    List<UserConsent> findLatestByUser(@Param("userId") UUID userId);

    /** The newest answer for one purpose, or null when never asked. */
    Boolean findLatestGranted(@Param("userId") UUID userId, @Param("purpose") String purpose);
}
