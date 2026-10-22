package com.ds.goroute.mapper;

import com.ds.goroute.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.UUID;

@Mapper
public interface UserMapper {
    int insert(User user);
    
    User selectByEmail(@Param("email") String email);
    
    User selectByEmailIncludingDeleted(@Param("email") String email);
    
    User selectByUsername(@Param("username") String username);

    boolean existsByUsername(@Param("username") String username);
    
    User selectByProviderId(@Param("providerId") String providerId);
    
    User selectById(@Param("id") UUID id);
    
    java.util.List<User> selectByIds(@Param("ids") java.util.Collection<UUID> ids);

    java.util.List<User> selectAll();

    java.util.List<User> selectFollowers(@Param("userId") UUID userId);

    java.util.List<User> selectFollowing(@Param("userId") UUID userId);

    int countFollowers(@Param("userId") UUID userId);

    int countFollowing(@Param("userId") UUID userId);

    /**
     * Discover page and the two follow lists all render the same card. These three read
     * the page and its four counts in one statement instead of one profile query plus
     * four counts per row.
     */
    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectDiscoverUsers(
            @Param("userId") UUID userId, @Param("limit") int limit);

    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectFollowerProfiles(
            @Param("userId") UUID userId);

    /** People matching {@code query} (lower case, LIKE-escaped), never the viewer or anyone blocked either way. */
    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectSearchUsers(
            @Param("userId") UUID userId, @Param("query") String query, @Param("limit") int limit);

    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectFollowingProfiles(
            @Param("userId") UUID userId);

    /**
     * Feed ranking by public check-ins and long public trips posted at or after {@code since}
     * (UTC); {@code viewerId} may be null for guests.
     */
    java.util.List<com.ds.goroute.dto.response.TopActiveUserResponse> selectTopActiveUsers(
            @Param("viewerId") UUID viewerId,
            @Param("since") java.time.LocalDateTime since,
            @Param("checkinPoints") int checkinPoints,
            @Param("checkinWithReviewPoints") int checkinWithReviewPoints,
            @Param("minTripActivities") int minTripActivities,
            @Param("maxTripPoints") int maxTripPoints,
            @Param("limit") int limit);

    int insertFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    int deleteFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    boolean existsFollow(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    java.util.List<UUID> selectFollowerIdChunk(@Param("authorId") UUID authorId,
                                               @Param("afterFollowerId") UUID afterFollowerId,
                                               @Param("betaUsernames") java.util.Collection<String> betaUsernames,
                                               @Param("limit") int limit);    
    int updateById(User user);

    int updateLastLoginAt(@Param("id") UUID id);
    
    int deleteById(@Param("id") UUID id);
    
    int softDeleteById(@Param("id") UUID id);

    int updatePassword(@Param("id") UUID id,
                       @Param("passwordHash") String passwordHash,
                       @Param("mustChangePassword") boolean mustChangePassword,
                       @Param("changedAt") java.time.LocalDateTime changedAt);

    /** Self-declared birth year (meetups, epic 18); never shown to other users. */
    int updateBirthYear(@Param("id") UUID id,
                        @Param("birthYear") int birthYear,
                        @Param("updatedAt") java.time.LocalDateTime updatedAt);

    /** IANA zone the user's device last reported; no-op when unchanged. */
    int updateTimeZone(@Param("id") UUID id, @Param("timeZone") String timeZone);

    String selectTimeZone(@Param("id") UUID id);

    /** People cards (discover counts) for these ids; deleted and non-human accounts are absent. */
    java.util.List<com.ds.goroute.dto.response.DiscoverUserResponse> selectDiscoverProfilesByIds(
            @Param("ids") java.util.Collection<UUID> ids);

    /** Feed search index: the next {@code limit} findable people after {@code afterId}, by id. */
    java.util.List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexPage(
            @Param("afterId") UUID afterId, @Param("limit") int limit);

    /** Feed search index: accounts changed after the cursor, findable or not, oldest change first. */
    java.util.List<com.ds.goroute.entity.SocialSearchIndexRow> selectSearchIndexChanges(
            @Param("afterUpdatedAt") java.time.LocalDateTime afterUpdatedAt,
            @Param("afterId") UUID afterId,
            @Param("limit") int limit);

    java.time.LocalDateTime selectSearchIndexMaxUpdatedAt();
}
