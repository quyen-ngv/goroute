package com.ds.goroute.mapper;

import com.ds.goroute.entity.SavedPost;
import com.ds.goroute.type.SavedPostType;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Mapper
public interface SavedPostMapper {

    /** Idempotent: saving an already saved post keeps the first save time. */
    int insert(@Param("userId") UUID userId,
               @Param("postType") SavedPostType postType,
               @Param("postId") UUID postId);

    int delete(@Param("userId") UUID userId,
               @Param("postType") SavedPostType postType,
               @Param("postId") UUID postId);

    /**
     * One page of the user's saves. {@code oldestFirst} flips the order; the after* triple is the
     * last row of the previous page (all null for the first page).
     */
    List<SavedPost> findPage(@Param("userId") UUID userId,
                             @Param("oldestFirst") boolean oldestFirst,
                             @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
                             @Param("afterPostType") String afterPostType,
                             @Param("afterPostId") UUID afterPostId,
                             @Param("limit") int limit);

    int countByUser(@Param("userId") UUID userId);

    /** Of {@code postIds}, the ones this user saved. For marking a page of posts in one query. */
    List<UUID> findSavedIds(@Param("userId") UUID userId,
                            @Param("postType") SavedPostType postType,
                            @Param("postIds") Collection<UUID> postIds);
}
