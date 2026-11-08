package com.ds.goroute.mapper;

import com.ds.goroute.entity.Settlement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SettlementMapper {
    int insert(Settlement settlement);

    int updateById(Settlement settlement);

    Settlement selectById(@Param("id") UUID id);

    int softDelete(@Param("id") UUID id);

    int restore(@Param("id") UUID id);

    List<Settlement> selectActiveByTripIds(@Param("tripIds") Collection<UUID> tripIds);

    List<Settlement> selectTripPage(@Param("tripId") UUID tripId,
                                    @Param("includeDeleted") boolean includeDeleted,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    long countTrip(@Param("tripId") UUID tripId, @Param("includeDeleted") boolean includeDeleted);

    List<Settlement> selectActiveBySplitIds(@Param("splitIds") Collection<UUID> splitIds);

    int softDeleteBySplitIds(@Param("splitIds") Collection<UUID> splitIds);

    int moveGuestFromToUser(@Param("guestMemberId") UUID guestMemberId, @Param("userId") UUID userId);

    int moveGuestToToUser(@Param("guestMemberId") UUID guestMemberId, @Param("userId") UUID userId);

    int softDeleteSelfSettlements(@Param("userId") UUID userId);
}
