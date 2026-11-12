package com.ds.goroute.repository.impl;

import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.entity.PostPlaceRef;
import com.ds.goroute.mapper.PostMapper;
import com.ds.goroute.repository.PostRepository;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostMapper mapper;

    @Override
    public void insert(Post post) {
        mapper.insert(post);
    }

    @Override
    public Optional<Post> findById(UUID id) {
        return id == null ? Optional.empty() : Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public List<Post> findByIds(Collection<UUID> ids) {
        // An empty IN () is a syntax error, and the answer is knowable here.
        return ids == null || ids.isEmpty() ? List.of() : mapper.findByIds(ids);
    }

    @Override
    public int updateContent(Post post) {
        return mapper.updateContent(post);
    }

    @Override
    public boolean publishFirstTime(UUID postId, UUID actorId) {
        return mapper.publishFirstTime(postId, actorId) == 1;
    }

    @Override
    public int updateStatus(UUID postId, PostStatus status, UUID actorId) {
        return mapper.updateStatus(postId, status.name(), actorId);
    }

    @Override
    public int markRemoved(UUID postId, UUID authorId, UUID actorId) {
        return mapper.markRemoved(postId, authorId, actorId);
    }

    @Override
    public List<Post> findFeed(UUID viewerId, FeedGeoFilter geo, int limit, int offset) {
        return mapper.findFeed(viewerId,
                geo == null ? null : geo.latitude(),
                geo == null ? null : geo.longitude(),
                geo == null ? null : geo.radiusKm(),
                limit, offset);
    }

    @Override
    public List<Post> findFollowing(UUID viewerId, int limit, int offset) {
        return mapper.findFollowing(viewerId, limit, offset);
    }

    @Override
    public List<Post> findByPlace(UUID placeId, UUID viewerId, int limit, int offset) {
        return mapper.findByPlace(placeId, viewerId, limit, offset);
    }

    @Override
    public List<Post> findByUser(UUID userId, boolean includeDrafts, int limit, int offset) {
        return mapper.findByUser(userId, includeDrafts, limit, offset);
    }

    @Override
    public Optional<Post> findLiveByUserAndTrip(UUID userId, UUID tripId) {
        return Optional.ofNullable(mapper.findLiveByUserAndTrip(userId, tripId));
    }

    @Override
    public List<Post> findForAdmin(String search, PostStatus status, PostType postType, UUID authorUserId,
                                   boolean managedOnly, int limit, int offset) {
        return mapper.findForAdmin(search, status == null ? null : status.name(),
                postType == null ? null : postType.name(), authorUserId, managedOnly, false, limit, offset);
    }

    @Override
    public void replacePlaces(UUID postId, List<UUID> placeIds) {
        mapper.deletePlaces(postId);
        if (placeIds != null && !placeIds.isEmpty()) {
            mapper.insertPlaces(postId, placeIds);
        }
    }

    @Override
    public List<PostPlaceRef> findPlaces(Collection<UUID> postIds) {
        return postIds == null || postIds.isEmpty() ? List.of() : mapper.findPlaces(postIds);
    }

    @Override
    public int insertLike(UUID postId, UUID userId) {
        int inserted = mapper.insertLike(postId, userId);
        if (inserted > 0) {
            mapper.refreshLikeCount(postId);
        }
        return inserted;
    }

    @Override
    public int deleteLike(UUID postId, UUID userId) {
        int deleted = mapper.deleteLike(postId, userId);
        if (deleted > 0) {
            mapper.refreshLikeCount(postId);
        }
        return deleted;
    }

    @Override
    public boolean hasLike(UUID postId, UUID userId) {
        return userId != null && mapper.hasLike(postId, userId);
    }

    @Override
    public Set<UUID> findLikedIds(UUID userId, Collection<UUID> postIds) {
        if (userId == null || postIds == null || postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(mapper.findLikedIds(userId, postIds));
    }

    @Override
    public void insertMedia(PostMedia media) {
        mapper.insertMedia(media);
    }

    @Override
    public Optional<PostMedia> findMediaById(UUID mediaId) {
        return mediaId == null ? Optional.empty() : Optional.ofNullable(mapper.findMediaById(mediaId));
    }

    @Override
    public List<PostMedia> findMedia(Collection<UUID> postIds) {
        return postIds == null || postIds.isEmpty() ? List.of() : mapper.findMedia(postIds);
    }

    @Override
    public int countMedia(UUID postId) {
        return mapper.countMedia(postId);
    }

    @Override
    public int softDeleteMedia(UUID postId, UUID mediaId) {
        return mapper.softDeleteMedia(mediaId, postId);
    }

    @Override
    public int softDeleteAllMedia(UUID postId) {
        return mapper.softDeleteAllMedia(postId);
    }
}
