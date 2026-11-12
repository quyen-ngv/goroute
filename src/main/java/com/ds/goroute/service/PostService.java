package com.ds.goroute.service;

import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.request.AttachPostMediaRequest;
import com.ds.goroute.dto.request.CreatePostRequest;
import com.ds.goroute.dto.response.PostTripMediaResponse;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.dto.response.PostLikeResponse;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostPolicyResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.type.PostMediaRole;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Blog posts in the app (epic 20, .docs/BLOG_POSTS_PLAN_2026-11-10.md §5). */
public interface PostService {

    PostPolicyResponse policy(UUID viewerId);

    SlicePageResponse<PostResponse> feed(UUID viewerId, FeedGeoFilter geo, int page, int size);

    SlicePageResponse<PostResponse> following(UUID viewerId, int page, int size);

    SlicePageResponse<PostResponse> byPlace(UUID viewerId, UUID placeId, int page, int size);

    SlicePageResponse<PostResponse> byUser(UUID viewerId, UUID userId, int page, int size);

    PostResponse get(UUID viewerId, UUID postId);

    PostLikeResponse setLike(UUID userId, UUID postId, Boolean liked);

    PostResponse create(UUID userId, CreatePostRequest request);

    PostResponse update(UUID userId, UUID postId, UpdatePostRequest request);

    PostResponse publish(UUID userId, UUID postId);

    PostResponse unpublish(UUID userId, UUID postId);

    void delete(UUID userId, UUID postId);

    PostMediaResponse uploadMedia(UUID userId, UUID postId, MultipartFile file, MultipartFile poster,
                                  PostMediaRole role, String caption, Integer durationSeconds);

    void deleteMedia(UUID userId, UUID postId, UUID mediaId);

    PostResponse tripDraft(UUID userId, UUID tripId);

    /** The author's own check-in photos and trip memories of a trip story's trip, matched to its stops. */
    PostTripMediaResponse tripMedia(UUID userId, UUID postId);

    /** Puts one of those on the post as a shared media row (no copy); the same file twice answers the first row. */
    PostMediaResponse attachTripMedia(UUID userId, UUID postId, AttachPostMediaRequest request);

    /**
     * Posts anyone may read right now -- published, public, live, not taken down -- keyed by id, as
     * list cards (no body). For saved posts and anything else holding post ids.
     */
    Map<UUID, PostResponse> findVisibleByIds(UUID viewerId, Collection<UUID> postIds);
}
