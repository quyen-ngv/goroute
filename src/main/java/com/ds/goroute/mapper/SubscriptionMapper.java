package com.ds.goroute.mapper;

import com.ds.goroute.entity.SubscriptionPlan;
import com.ds.goroute.entity.UserSubscription;
import com.ds.goroute.entity.UserSubscriptionGrant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SubscriptionMapper {

    List<SubscriptionPlan> findPlans(@Param("activeOnly") boolean activeOnly);

    SubscriptionPlan findPlan(@Param("code") String code);

    /** The row as stored, expiry included, for callers that must see a lapsed plan as it is. */
    UserSubscription findSubscription(@Param("userId") UUID userId);

    /**
     * Moves the account onto a plan, ending at {@code expiresAt}.
     *
     * <p>Takes the row lock so two grants landing together cannot both read the same end date and
     * both extend from it.
     */
    int applyPlan(@Param("userId") UUID userId,
                  @Param("tier") String tier,
                  @Param("planCode") String planCode,
                  @Param("startsAt") LocalDateTime startsAt,
                  @Param("expiresAt") LocalDateTime expiresAt);

    int revokePlan(@Param("userId") UUID userId, @Param("endedAt") LocalDateTime endedAt);

    /** The current end date under a row lock, so an extension reads and writes atomically. */
    LocalDateTime findExpiresAtForUpdate(@Param("userId") UUID userId);

    int insertGrant(UserSubscriptionGrant grant);

    UserSubscriptionGrant findGrantByReference(@Param("referenceKey") String referenceKey);

    List<UserSubscriptionGrant> findGrants(@Param("userId") UUID userId, @Param("limit") int limit);

    /** Position in this user's own sequence of grants, used to build a repeatable reference key. */
    int countGrants(@Param("userId") UUID userId);
}
