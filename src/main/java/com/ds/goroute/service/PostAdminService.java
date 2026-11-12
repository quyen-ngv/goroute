package com.ds.goroute.service;

import com.ds.goroute.dto.request.AdminCreatePostRequest;
import com.ds.goroute.dto.request.HidePostRequest;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Posts in the console (epic 20, .docs/BLOG_POSTS_PLAN_2026-11-10.md §6). Writing is only for posts
 * of accounts still managed by the team; hiding and showing apply to every post.
 */
public interface PostAdminService {

    SlicePageResponse<PostResponse> list(String search, PostStatus status, PostType postType,
                                         UUID authorUserId, boolean managedOnly, int page, int size);

    PostResponse get(UUID postId);

    PostResponse create(UUID adminId, AdminCreatePostRequest request);

    PostResponse update(UUID adminId, UUID postId, UpdatePostRequest request);

    PostResponse publish(UUID adminId, UUID postId);

    PostResponse unpublish(UUID adminId, UUID postId);

    void delete(UUID adminId, UUID postId);

    PostMediaResponse uploadMedia(UUID adminId, UUID postId, MultipartFile file, MultipartFile poster,
                                  PostMediaRole role, String caption, Integer durationSeconds);

    void deleteMedia(UUID adminId, UUID postId, UUID mediaId);

    PostResponse hide(UUID adminId, UUID postId, HidePostRequest request);

    PostResponse show(UUID adminId, UUID postId);
}
