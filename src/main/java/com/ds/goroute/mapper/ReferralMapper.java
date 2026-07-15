package com.ds.goroute.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface ReferralMapper {
    int countByInviteeId(@Param("inviteeId") UUID inviteeId);
    void insert(@Param("id") UUID id, @Param("inviterId") UUID inviterId,
                @Param("inviteeId") UUID inviteeId, @Param("code") String code);
}
