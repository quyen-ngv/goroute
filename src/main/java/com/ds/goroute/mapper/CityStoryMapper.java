package com.ds.goroute.mapper;

import com.ds.goroute.entity.CityStory;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Mapper
public interface CityStoryMapper {

    @Select("""
        SELECT cs.* FROM city_stories cs
        WHERE cs.deleted_at IS NULL
          AND cs.created_at >= #{since}
        ORDER BY cs.created_at DESC
        """)
    List<CityStory> selectActiveSince(@Param("since") LocalDateTime since);

    @Select("""
        SELECT cs.* FROM city_stories cs
        WHERE cs.location_image_id = #{locationImageId}
          AND cs.deleted_at IS NULL
        ORDER BY cs.created_at DESC
        """)
    List<CityStory> selectByLocationImageId(UUID locationImageId);

    @Select("""
        SELECT cs.* FROM city_stories cs
        WHERE cs.location_image_id = #{locationImageId}
          AND cs.deleted_at IS NULL
          AND cs.created_at >= #{since}
        ORDER BY cs.created_at ASC
        """)
    List<CityStory> selectActiveByLocationSince(
            @Param("locationImageId") UUID locationImageId,
            @Param("since") LocalDateTime since);

    @Select("""
        SELECT cs.* FROM city_stories cs
        WHERE cs.id = #{id} AND cs.deleted_at IS NULL
        """)
    CityStory selectById(UUID id);

    @Insert("""
        INSERT INTO city_stories (
            id, location_image_id, image_url, description, place_id,
            like_count, created_at, updated_at
        ) VALUES (
            #{id}, #{locationImageId}, #{imageUrl}, #{description}, #{placeId},
            #{likeCount}, #{createdAt}, #{updatedAt}
        )
        """)
    void insert(CityStory story);

    @Update("""
        UPDATE city_stories
        SET like_count = #{likeCount},
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    void updateLikeCount(CityStory story);

    @Update("""
        UPDATE city_stories
        SET deleted_at = #{deletedAt},
            updated_at = #{updatedAt}
        WHERE id = #{id}
        """)
    void softDelete(CityStory story);

    @Select("""
        SELECT COUNT(*) > 0 FROM city_story_likes
        WHERE story_id = #{storyId} AND user_id = #{userId}
        """)
    boolean existsLike(@Param("storyId") UUID storyId, @Param("userId") UUID userId);

    @Insert("""
        INSERT INTO city_story_likes (id, story_id, user_id, created_at)
        VALUES (#{id}, #{storyId}, #{userId}, #{createdAt})
        """)
    void insertLike(
            @Param("id") UUID id,
            @Param("storyId") UUID storyId,
            @Param("userId") UUID userId,
            @Param("createdAt") LocalDateTime createdAt);

    @Delete("""
        DELETE FROM city_story_likes
        WHERE story_id = #{storyId} AND user_id = #{userId}
        """)
    void deleteLike(@Param("storyId") UUID storyId, @Param("userId") UUID userId);

    @Select("""
        SELECT COUNT(*) > 0 FROM city_story_views
        WHERE story_id = #{storyId} AND user_id = #{userId}
        """)
    boolean existsView(@Param("storyId") UUID storyId, @Param("userId") UUID userId);

    @Insert("""
        INSERT INTO city_story_views (id, story_id, user_id, viewed_at)
        VALUES (#{id}, #{storyId}, #{userId}, #{viewedAt})
        ON CONFLICT (story_id, user_id) DO NOTHING
        """)
    void insertView(
            @Param("id") UUID id,
            @Param("storyId") UUID storyId,
            @Param("userId") UUID userId,
            @Param("viewedAt") LocalDateTime viewedAt);

    @Select("""
        SELECT story_id FROM city_story_views
        WHERE user_id = #{userId}
          AND story_id IN (
            SELECT id FROM city_stories
            WHERE deleted_at IS NULL AND created_at >= #{since}
          )
        """)
    List<UUID> selectViewedStoryIdsSince(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since);

    @Select("""
        SELECT story_id FROM city_story_likes
        WHERE user_id = #{userId}
          AND story_id IN (
            SELECT id FROM city_stories
            WHERE deleted_at IS NULL AND created_at >= #{since}
          )
        """)
    List<UUID> selectLikedStoryIdsSince(
            @Param("userId") UUID userId,
            @Param("since") LocalDateTime since);
}
