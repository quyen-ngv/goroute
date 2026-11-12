package com.ds.goroute.repository;

import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.entity.PostPlaceRef;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Persistence of the post aggregate (epic 20): the post, its places, its likes and its media rows.
 * Access rules live in the services; this answers what is stored.
 */
public interface PostRepository {

    void insert(Post post);

    /** The post with its body, removed or not. */
    Optional<Post> findById(UUID id);

    /** Without bodies; missing ids are absent. */
    List<Post> findByIds(Collection<UUID> ids);

    /** Rows changed: 1, or 0 when {@code post.version} is stale or the post was removed. */
    int updateContent(Post post);

    /** True when this call published the post for the first time (published_at was NULL). */
    boolean publishFirstTime(UUID postId, UUID actorId);

    /** Rows changed: 0 when the post already had this status (or was never published, for PUBLISHED). */
    int updateStatus(UUID postId, PostStatus status, UUID actorId);

    /** Rows changed: 1, or 0 when it was already removed. */
    int markRemoved(UUID postId, UUID authorId, UUID actorId);

    List<Post> findFeed(UUID viewerId, FeedGeoFilter geo, int limit, int offset);

    List<Post> findFollowing(UUID viewerId, int limit, int offset);

    List<Post> findByPlace(UUID placeId, UUID viewerId, int limit, int offset);

    List<Post> findByUser(UUID userId, boolean includeDrafts, int limit, int offset);

    Optional<Post> findLiveByUserAndTrip(UUID userId, UUID tripId);

    List<Post> findForAdmin(String search, PostStatus status, PostType postType, UUID authorUserId,
                            boolean managedOnly, int limit, int offset);

    /** Replaces the post's places with these, in this order. */
    void replacePlaces(UUID postId, List<UUID> placeIds);

    List<PostPlaceRef> findPlaces(Collection<UUID> postIds);

    /** Rows changed: 1 when the like is new. Keeps posts.like_count in step. */
    int insertLike(UUID postId, UUID userId);

    /** Rows changed: 1 when a like was there. Keeps posts.like_count in step. */
    int deleteLike(UUID postId, UUID userId);

    boolean hasLike(UUID postId, UUID userId);

    Set<UUID> findLikedIds(UUID userId, Collection<UUID> postIds);

    void insertMedia(PostMedia media);

    /** A live media row of some post; the caller checks it belongs to the post it acts on. */
    Optional<PostMedia> findMediaById(UUID mediaId);

    /** Every live media of these posts, by post then position. */
    List<PostMedia> findMedia(Collection<UUID> postIds);

    int countMedia(UUID postId);

    int softDeleteMedia(UUID postId, UUID mediaId);

    int softDeleteAllMedia(UUID postId);
}
