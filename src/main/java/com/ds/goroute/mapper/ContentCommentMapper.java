package com.ds.goroute.mapper;

import com.ds.goroute.entity.ContentComment;
import com.ds.goroute.entity.ContentCommentCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;
import java.util.UUID;

@Mapper
public interface ContentCommentMapper {
    int insert(ContentComment comment);

    ContentComment findById(@Param("id") UUID id);

    ContentComment findByIdWithStats(@Param("id") UUID id, @Param("viewerId") UUID viewerId);

    List<ContentComment> findByTarget(@Param("contentType") String contentType,
                                      @Param("contentId") UUID contentId);

    /**
     * The number findByTarget was only ever loaded to produce. Same target predicate,
     * plus the two visibility rules the caller applied in memory: not soft deleted and
     * not under an unrestored takedown.
     */
    int countActiveByTarget(@Param("contentType") String contentType,
                            @Param("contentId") UUID contentId);

    /**
     * {@link #countActiveByTarget} for a whole page of targets in one grouped query. Targets
     * with no active comment are absent from the result. With {@code excludeTakenDownTargets}
     * a target under an unrestored takedown counts nothing, because the per-target endpoint
     * refuses to read its thread.
     */
    List<ContentCommentCount> countActiveByTargets(@Param("contentType") String contentType,
                                                   @Param("contentIds") List<UUID> contentIds,
                                                   @Param("excludeTakenDownTargets") boolean excludeTakenDownTargets);

    List<ContentComment> findPage(@Param("contentType") String contentType,
                                  @Param("contentId") UUID contentId,
                                  @Param("parentId") UUID parentId,
                                  @Param("afterCreatedAt") LocalDateTime afterCreatedAt,
                                  @Param("afterId") UUID afterId,
                                  @Param("limit") int limit,
                                  @Param("viewerId") UUID viewerId);

    int softDelete(@Param("id") UUID id);

    int updateContent(@Param("id") UUID id, @Param("content") String content);

    boolean hasLike(@Param("commentId") UUID commentId, @Param("userId") UUID userId);

    int insertLike(@Param("commentId") UUID commentId, @Param("userId") UUID userId);

    int deleteLike(@Param("commentId") UUID commentId, @Param("userId") UUID userId);
}
