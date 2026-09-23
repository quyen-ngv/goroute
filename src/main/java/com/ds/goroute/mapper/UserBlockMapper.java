package com.ds.goroute.mapper;

import com.ds.goroute.entity.UserBlock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface UserBlockMapper {

    int insert(UserBlock block);

    int delete(@Param("blockerId") UUID blockerId, @Param("blockedId") UUID blockedId);

    /** Everyone this person has blocked, newest first. */
    List<UserBlock> selectByBlocker(@Param("blockerId") UUID blockerId);

    /**
     * Whether either of these two has blocked the other.
     *
     * <p>One query rather than two, and deliberately blind to direction: a conversation
     * needs both sides willing, so which of them said no does not change the answer and
     * telling the caller which would leak the decision back to the person it was made about.
     */
    boolean existsBetween(@Param("first") UUID first, @Param("second") UUID second);

    /**
     * Of [candidates], those who have blocked [viewer] or whom [viewer] has blocked.
     *
     * <p>For lists: a page of conversation participants or of profiles asks this once
     * instead of once per row.
     */
    List<UUID> selectBlockedAmong(@Param("viewer") UUID viewer,
                                  @Param("candidates") Collection<UUID> candidates);
}
