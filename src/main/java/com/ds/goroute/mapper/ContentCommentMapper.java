package com.ds.goroute.mapper;

import com.ds.goroute.entity.ContentComment;
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

    int countActiveByTarget(@Param("contentType") String contentType,
                            @Param("contentId") UUID contentId);
}
