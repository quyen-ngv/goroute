package com.ds.goroute.mapper;

import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.entity.PostPlaceRef;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Blog posts (epic 20): posts, post_places, post_likes and the posts' media_assets rows. */
@Mapper
public interface PostMapper {

    int insert(Post post);

    /** With the body, removed rows included; the service decides who may see what. */
    Post findById(@Param("id") UUID id);

    /** Without the body. */
    List<Post> findByIds(@Param("ids") Collection<UUID> ids);

    int updateContent(Post post);

    int publishFirstTime(@Param("id") UUID id, @Param("actorId") UUID actorId);

    int updateStatus(@Param("id") UUID id, @Param("status") String status, @Param("actorId") UUID actorId);

    int markRemoved(@Param("id") UUID id, @Param("userId") UUID userId, @Param("actorId") UUID actorId);

    int refreshLikeCount(@Param("id") UUID id);

    List<Post> findFeed(@Param("viewerId") UUID viewerId,
                        @Param("latitude") BigDecimal latitude,
                        @Param("longitude") BigDecimal longitude,
                        @Param("radiusKm") BigDecimal radiusKm,
                        @Param("limit") int limit,
                        @Param("offset") int offset);

    List<Post> findFollowing(@Param("viewerId") UUID viewerId,
                             @Param("limit") int limit,
                             @Param("offset") int offset);

    List<Post> findByPlace(@Param("placeId") UUID placeId,
                           @Param("viewerId") UUID viewerId,
                           @Param("limit") int limit,
                           @Param("offset") int offset);

    List<Post> findByUser(@Param("userId") UUID userId,
                          @Param("includeDrafts") boolean includeDrafts,
                          @Param("limit") int limit,
                          @Param("offset") int offset);

    Post findLiveByUserAndTrip(@Param("userId") UUID userId, @Param("tripId") UUID tripId);

    List<Post> findForAdmin(@Param("search") String search,
                            @Param("status") String status,
                            @Param("postType") String postType,
                            @Param("authorUserId") UUID authorUserId,
                            @Param("managedOnly") boolean managedOnly,
                            @Param("includeRemoved") boolean includeRemoved,
                            @Param("limit") int limit,
                            @Param("offset") int offset);

    int deletePlaces(@Param("postId") UUID postId);

    int insertPlaces(@Param("postId") UUID postId, @Param("placeIds") List<UUID> placeIds);

    List<PostPlaceRef> findPlaces(@Param("postIds") Collection<UUID> postIds);

    int insertLike(@Param("postId") UUID postId, @Param("userId") UUID userId);

    int deleteLike(@Param("postId") UUID postId, @Param("userId") UUID userId);

    boolean hasLike(@Param("postId") UUID postId, @Param("userId") UUID userId);

    List<UUID> findLikedIds(@Param("userId") UUID userId, @Param("postIds") Collection<UUID> postIds);

    int insertMedia(PostMedia media);

    PostMedia findMediaById(@Param("id") UUID id);

    List<PostMedia> findMedia(@Param("postIds") Collection<UUID> postIds);

    int countMedia(@Param("postId") UUID postId);

    int softDeleteMedia(@Param("id") UUID id, @Param("postId") UUID postId);

    int softDeleteAllMedia(@Param("postId") UUID postId);
}
