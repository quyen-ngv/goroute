package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AdminCreatePostRequest;
import com.ds.goroute.dto.request.HidePostRequest;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.entity.ContentTakedown;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ModerationFlagRepository;
import com.ds.goroute.repository.PostRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PostAdminService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.post.PostEditor;
import com.ds.goroute.service.post.PostResponseAssembler;
import com.ds.goroute.service.post.PostResponseAssembler.View;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import com.ds.goroute.utils.ErrorMessages;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Posts in the console (epic 20).
 *
 * <p>The console writes as a managed account: posts.user_id is the account, created_by / updated_by
 * the admin. Once an account is handed over its posts are the owner's, and every write answers
 * ACCOUNT_NOT_MANAGED; the console can still read them and hide or show them like any other post.
 * The app's POST switches never apply here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostAdminServiceImpl implements PostAdminService {

    private static final int MAX_PAGE_SIZE = 100;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostEditor editor;
    private final PostResponseAssembler assembler;
    private final TripAccessGuard tripAccessGuard;
    private final ModerationFlagRepository flagRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional(readOnly = true)
    public SlicePageResponse<PostResponse> list(String search, PostStatus status, PostType postType,
                                                UUID authorUserId, boolean managedOnly, int page, int size) {
        int limit = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        int safePage = Math.max(0, page);
        String trimmedSearch = search == null || search.isBlank() ? null : search.trim();
        List<Post> fetched = postRepository.findForAdmin(trimmedSearch, status, postType, authorUserId,
                managedOnly, limit + 1, safePage * limit);
        boolean hasMore = fetched.size() > limit;
        List<Post> posts = hasMore ? fetched.subList(0, limit) : fetched;
        return SlicePageResponse.<PostResponse>builder()
                .items(assembler.toResponses(posts, View.console(false)))
                .page(safePage)
                .size(limit)
                .hasMore(hasMore)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse get(UUID postId) {
        return detail(requireLive(postId));
    }

    @Override
    @Transactional
    public PostResponse create(UUID adminId, AdminCreatePostRequest request) {
        User author = userRepository.findById(request.getAuthorUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.USER_NOT_FOUND, "User not found"));
        requireManaged(author);
        if (request.getTripId() != null) {
            // The story may only be about a trip the account itself is on.
            tripAccessGuard.requireAccess(request.getTripId(), author.getId());
        }
        Post post = editor.createDraft(author.getId(), adminId, request.getPostType(), request.getTitle(),
                request.getTripId());
        return detail(requireLive(post.getId()));
    }

    @Override
    @Transactional
    public PostResponse update(UUID adminId, UUID postId, UpdatePostRequest request) {
        editor.update(requireManagedPost(postId), request, adminId);
        return detail(requireLive(postId));
    }

    @Override
    @Transactional
    public PostResponse publish(UUID adminId, UUID postId) {
        editor.publish(requireManagedPost(postId), adminId);
        return detail(requireLive(postId));
    }

    @Override
    @Transactional
    public PostResponse unpublish(UUID adminId, UUID postId) {
        editor.unpublish(requireManagedPost(postId), adminId);
        return detail(requireLive(postId));
    }

    @Override
    @Transactional
    public void delete(UUID adminId, UUID postId) {
        editor.remove(requireManagedPost(postId), adminId);
        log.info("Post {} removed by admin {}", postId, adminId);
    }

    /** Not transactional: the file upload must not hold a database connection. */
    @Override
    public PostMediaResponse uploadMedia(UUID adminId, UUID postId, MultipartFile file, MultipartFile poster,
                                         PostMediaRole role, String caption, Integer durationSeconds) {
        Post post = requireManagedPost(postId);
        return PostResponseAssembler.toMedia(
                editor.uploadMedia(post, adminId, file, poster, role, caption, durationSeconds));
    }

    @Override
    @Transactional
    public void deleteMedia(UUID adminId, UUID postId, UUID mediaId) {
        editor.deleteMedia(requireManagedPost(postId), mediaId);
    }

    @Override
    @Transactional
    public PostResponse hide(UUID adminId, UUID postId, HidePostRequest request) {
        Post post = requireLive(postId);
        flagRepository.insertTakedown(ContentTakedown.builder()
                .id(UUID.randomUUID())
                .contentType(ModeratedContentType.POST)
                .contentId(postId)
                .ownerId(post.getUserId())
                .category(request.getCategory())
                .reason(request.getReason())
                .removedBy(adminId)
                .removedAt(LocalDateTime.now())
                .build());
        notifyOwner(post, adminId, request.getReason());
        log.info("Post {} hidden by admin {}", postId, adminId);
        return detail(post);
    }

    @Override
    @Transactional
    public PostResponse show(UUID adminId, UUID postId) {
        Post post = requireLive(postId);
        flagRepository.restoreTakedown(ModeratedContentType.POST.name(), postId, adminId);
        log.info("Post {} shown again by admin {}", postId, adminId);
        return detail(post);
    }

    private Post requireLive(UUID postId) {
        return postRepository.findById(postId)
                .filter(post -> !Boolean.TRUE.equals(post.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Post not found"));
    }

    /** A live post whose author the console still holds. */
    private Post requireManagedPost(UUID postId) {
        Post post = requireLive(postId);
        User author = userRepository.findById(post.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.ACCOUNT_NOT_MANAGED,
                        "This account is no longer managed by the team"));
        requireManaged(author);
        return post;
    }

    private static void requireManaged(User author) {
        if (!Boolean.TRUE.equals(author.getManagedByAdmin())) {
            throw new BusinessException(ErrorConstant.ACCOUNT_NOT_MANAGED,
                    "This account is no longer managed by the team");
        }
    }

    private PostResponse detail(Post post) {
        return assembler.toResponse(post, View.console(true));
    }

    /** A silent hide creates a support ticket; a failed notification must not undo it either. */
    private void notifyOwner(Post post, UUID adminId, String reason) {
        try {
            notificationService.createNotification(
                    post.getUserId(),
                    null,
                    NotificationType.ADMIN_MESSAGE,
                    ErrorMessages.of(ErrorConstant.CONTENT_TAKEN_DOWN),
                    reason,
                    Map.of("contentType", ModeratedContentType.POST.name(),
                            "contentId", post.getId().toString()),
                    adminId);
        } catch (RuntimeException exception) {
            log.warn("Could not notify {} about a post takedown: {}", post.getUserId(), exception.getMessage(), exception);
        }
    }
}
