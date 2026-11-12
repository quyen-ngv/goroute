package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.FeedGeoFilter;
import com.ds.goroute.dto.request.AttachPostMediaRequest;
import com.ds.goroute.dto.request.CreatePostRequest;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.dto.response.PostTripMediaItemResponse;
import com.ds.goroute.dto.response.PostTripMediaResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserCheckinPhoto;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.MediaAssetRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.post.TripStoryMediaCollector;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.PostMediaType;
import com.ds.goroute.dto.response.PostLikeResponse;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostPolicyResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.SlicePageResponse;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PostRepository;
import com.ds.goroute.service.BetaAccessService;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.PostService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.notification.SocialNotificationService;
import com.ds.goroute.service.post.PostEditor;
import com.ds.goroute.service.post.PostResponseAssembler;
import com.ds.goroute.service.post.PostResponseAssembler.View;
import com.ds.goroute.service.post.TripStoryDraftBuilder;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.PostMediaRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Blog posts in the app (epic 20).
 *
 * <p>Two switches, each "config or beta user": POST.ENABLED decides whether lists show posts at
 * all, POST.USER_WRITE_ENABLED whether a user may write one. A post opened by its id is readable
 * whatever ENABLED says, so a link already shared keeps working. Removing, unpublishing and
 * deleting media stay open to the author with writing switched off: taking something down must
 * never need the feature that put it up.
 */
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private static final int MAX_PAGE_SIZE = 50;

    private final PostRepository postRepository;
    private final PostEditor editor;
    private final PostResponseAssembler assembler;
    private final TripStoryDraftBuilder tripStoryDraftBuilder;
    private final TripStoryMediaCollector tripStoryMediaCollector;
    private final ActivityRepository activityRepository;
    private final UserCheckinRepository checkinRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final TripAccessGuard tripAccessGuard;
    private final ContentModerationService contentModerationService;
    private final SocialNotificationService socialNotificationService;
    private final BusinessConfigService config;
    private final BetaAccessService betaAccess;

    @Override
    @Transactional(readOnly = true)
    public PostPolicyResponse policy(UUID viewerId) {
        return PostPolicyResponse.builder()
                .enabled(isEnabledFor(viewerId))
                .canWrite(canWrite(viewerId))
                .maxBodyLength(config.getInt(BusinessConfigKey.POST_MAX_BODY_LENGTH))
                .maxMedia(config.getInt(BusinessConfigKey.POST_MAX_MEDIA))
                .maxVideoSeconds(config.getInt(BusinessConfigKey.POST_MAX_VIDEO_SECONDS))
                .maxPlaces(config.getInt(BusinessConfigKey.POST_MAX_PLACES))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public SlicePageResponse<PostResponse> feed(UUID viewerId, FeedGeoFilter geo, int page, int size) {
        int limit = boundedSize(size);
        if (!isEnabledFor(viewerId)) {
            return SlicePageResponse.of(List.of(), boundedPage(page), limit);
        }
        List<Post> posts = postRepository.findFeed(viewerId, geo, limit + 1, boundedPage(page) * limit);
        return page(posts, viewerId, page, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public SlicePageResponse<PostResponse> following(UUID viewerId, int page, int size) {
        int limit = boundedSize(size);
        if (!isEnabledFor(viewerId)) {
            return SlicePageResponse.of(List.of(), boundedPage(page), limit);
        }
        return page(postRepository.findFollowing(viewerId, limit + 1, boundedPage(page) * limit),
                viewerId, page, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public SlicePageResponse<PostResponse> byPlace(UUID viewerId, UUID placeId, int page, int size) {
        int limit = boundedSize(size);
        if (!isEnabledFor(viewerId)) {
            return SlicePageResponse.of(List.of(), boundedPage(page), limit);
        }
        return page(postRepository.findByPlace(placeId, viewerId, limit + 1, boundedPage(page) * limit),
                viewerId, page, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public SlicePageResponse<PostResponse> byUser(UUID viewerId, UUID userId, int page, int size) {
        int limit = boundedSize(size);
        if (!isEnabledFor(viewerId)) {
            return SlicePageResponse.of(List.of(), boundedPage(page), limit);
        }
        boolean isOwner = userId.equals(viewerId);
        return page(postRepository.findByUser(userId, isOwner, limit + 1, boundedPage(page) * limit),
                viewerId, page, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public PostResponse get(UUID viewerId, UUID postId) {
        Post post = requireLive(postId);
        boolean isAuthor = post.getUserId().equals(viewerId);
        if (!isAuthor) {
            // A draft or a private post does not exist for anyone else; same answer as a wrong id.
            if (!post.isPubliclyReadable()) {
                throw notFound();
            }
            if (contentModerationService.isTakenDown(ModeratedContentType.POST, postId)) {
                throw new BusinessException(ErrorConstant.CONTENT_TAKEN_DOWN, "This content has been removed.");
            }
        }
        return detail(post, viewerId);
    }

    @Override
    @Transactional
    public PostLikeResponse setLike(UUID userId, UUID postId, Boolean liked) {
        Post post = postRepository.findById(postId)
                .filter(Post::isPubliclyReadable)
                .orElseThrow(PostServiceImpl::notFound);
        if (contentModerationService.isTakenDown(ModeratedContentType.POST, postId)) {
            throw new BusinessException(ErrorConstant.CONTENT_TAKEN_DOWN, "This content has been removed.");
        }
        // {@code liked} is the state the viewer wants, so sending it twice changes nothing; without
        // it the like flips, as for a check-in.
        boolean hasLiked = postRepository.hasLike(postId, userId);
        boolean wantLiked = liked == null ? !hasLiked : liked;
        if (hasLiked && !wantLiked) {
            postRepository.deleteLike(postId, userId);
        } else if (!hasLiked && wantLiked && postRepository.insertLike(postId, userId) > 0) {
            socialNotificationService.notifyLike(post.getUserId(), userId, ModeratedContentType.POST.name(), postId);
        }
        Post refreshed = postRepository.findById(postId).orElse(post);
        return PostLikeResponse.builder()
                .postId(postId)
                .likeCount(refreshed.getLikeCount() == null ? 0 : refreshed.getLikeCount())
                .hasLiked(wantLiked)
                .build();
    }

    @Override
    @Transactional
    public PostResponse create(UUID userId, CreatePostRequest request) {
        requireWrite(userId);
        if (request.getTripId() != null) {
            // The trip decides what the story may show, so a stranger's trip id is refused here.
            tripAccessGuard.requireAccess(request.getTripId(), userId);
        }
        Post post = editor.createDraft(userId, userId, request.getPostType(), request.getTitle(), request.getTripId());
        return detail(requireLive(post.getId()), userId);
    }

    @Override
    @Transactional
    public PostResponse update(UUID userId, UUID postId, UpdatePostRequest request) {
        requireWrite(userId);
        Post post = requireOwned(userId, postId);
        editor.update(post, request, userId);
        return detail(requireLive(postId), userId);
    }

    @Override
    @Transactional
    public PostResponse publish(UUID userId, UUID postId) {
        requireWrite(userId);
        editor.publish(requireOwned(userId, postId), userId);
        return detail(requireLive(postId), userId);
    }

    @Override
    @Transactional
    public PostResponse unpublish(UUID userId, UUID postId) {
        editor.unpublish(requireOwned(userId, postId), userId);
        return detail(requireLive(postId), userId);
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID postId) {
        editor.remove(requireOwned(userId, postId), userId);
    }

    /** Not transactional: the file upload must not hold a database connection. */
    @Override
    public PostMediaResponse uploadMedia(UUID userId, UUID postId, MultipartFile file, MultipartFile poster,
                                         PostMediaRole role, String caption, Integer durationSeconds) {
        requireWrite(userId);
        Post post = requireOwned(userId, postId);
        return PostResponseAssembler.toMedia(
                editor.uploadMedia(post, userId, file, poster, role, caption, durationSeconds));
    }

    @Override
    @Transactional
    public void deleteMedia(UUID userId, UUID postId, UUID mediaId) {
        editor.deleteMedia(requireOwned(userId, postId), mediaId);
    }

    @Override
    @Transactional
    public PostResponse tripDraft(UUID userId, UUID tripId) {
        requireWrite(userId);
        Trip trip = tripAccessGuard.requireAccess(tripId, userId);
        // One story per author and trip: asking again opens the one they already started.
        Post post = postRepository.findLiveByUserAndTrip(userId, tripId)
                .orElseGet(() -> tripStoryDraftBuilder.build(trip, userId));
        return detail(requireLive(post.getId()), userId);
    }

    @Override
    @Transactional(readOnly = true)
    public PostTripMediaResponse tripMedia(UUID userId, UUID postId) {
        requireWrite(userId);
        Post post = requireOwned(userId, postId);
        if (post.getTripId() == null) {
            return PostTripMediaResponse.builder().items(List.of()).build();
        }
        Trip trip = tripAccessGuard.requireAccess(post.getTripId(), userId);
        List<Activity> activities = liveActivities(trip.getId());
        Map<String, UUID> attachedByUrl = new java.util.HashMap<>();
        for (PostMedia media : postRepository.findMedia(List.of(post.getId()))) {
            attachedByUrl.putIfAbsent(media.getUrl(), media.getId());
        }
        List<PostTripMediaItemResponse> items = tripStoryMediaCollector.collect(trip, userId, activities).stream()
                .map(item -> PostTripMediaItemResponse.builder()
                        .sourceType(item.sourceType())
                        .sourceId(item.sourceId())
                        .checkinId(item.checkinId())
                        .mediaType(item.mediaType())
                        .url(item.url())
                        .thumbnailUrl(item.thumbnailUrl())
                        .durationSeconds(item.durationSeconds())
                        .caption(item.caption())
                        .takenAt(item.takenAt())
                        .visibility(item.visibility())
                        .activityId(item.activityId())
                        .suggested(item.suggested())
                        .attachedMediaId(attachedByUrl.get(item.url()))
                        .build())
                .toList();
        return PostTripMediaResponse.builder().items(items).build();
    }

    @Override
    @Transactional
    public PostMediaResponse attachTripMedia(UUID userId, UUID postId, AttachPostMediaRequest request) {
        requireWrite(userId);
        Post post = requireOwned(userId, postId);
        if (post.getTripId() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Only a trip story borrows trip photos");
        }
        PostMedia media = switch (request.getSourceType()) {
            case CHECKIN_PHOTO -> attachCheckinPhoto(userId, post, request.getCheckinId(), request.getSourceId());
            case TRIP_MEMORY -> attachMemory(userId, post, request.getSourceId());
        };
        return PostResponseAssembler.toMedia(media);
    }

    /** The author's own check-in, live, and one of its photos (a video with its poster). */
    private PostMedia attachCheckinPhoto(UUID userId, Post post, UUID checkinId, UUID photoId) {
        if (checkinId == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "checkinId is required for a check-in photo");
        }
        UserCheckin checkin = checkinRepository.findById(checkinId)
                .filter(found -> userId.equals(found.getUserId()) && !Boolean.TRUE.equals(found.getIsRemoved()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Check-in not found"));
        UserCheckinPhoto photo = checkinRepository.findPhotos(checkin.getId()).stream()
                .filter(found -> photoId.equals(found.getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Photo not found"));
        if (photo.isVideo() && (photo.getThumbnailUrl() == null || photo.getThumbnailUrl().isBlank())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "This video has no poster frame");
        }
        return editor.attachShared(post, userId,
                photo.isVideo() ? PostMediaType.VIDEO : PostMediaType.IMAGE, photo.getUrl(),
                photo.isVideo() ? photo.getThumbnailUrl() : null, photo.isVideo() ? photo.getDurationSeconds() : null);
    }

    /** A memory photo the author uploaded to this very trip. */
    private PostMedia attachMemory(UUID userId, Post post, UUID memoryId) {
        MediaAsset memory = mediaAssetRepository.findById(memoryId)
                .filter(found -> found.getDeletedAt() == null
                        && userId.equals(found.getUploadedBy())
                        && post.getTripId().equals(found.getTripId())
                        && (found.getMediaType() == null || "IMAGE".equalsIgnoreCase(found.getMediaType())))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Memory not found"));
        return editor.attachShared(post, userId, PostMediaType.IMAGE, memory.getUrl(), null, null);
    }

    private List<Activity> liveActivities(UUID tripId) {
        return activityRepository.findByTripId(tripId).stream()
                .filter(activity -> activity.getStatus() != ActivityStatus.REJECTED)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PostResponse> findVisibleByIds(UUID viewerId, Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        List<Post> live = postRepository.findByIds(postIds).stream()
                .filter(Post::isPubliclyReadable)
                .toList();
        Set<UUID> takenDown = contentModerationService.takenDownIds(ModeratedContentType.POST,
                live.stream().map(Post::getId).toList());
        List<Post> visible = live.stream().filter(post -> !takenDown.contains(post.getId())).toList();
        return assembler.toResponses(visible, View.app(viewerId, false, canWrite(viewerId))).stream()
                .collect(Collectors.toMap(PostResponse::getId, Function.identity(), (first, second) -> first));
    }

    // --- rules ---------------------------------------------------------------------------

    private boolean isEnabledFor(UUID viewerId) {
        return config.getBoolean(BusinessConfigKey.POST_ENABLED)
                || (viewerId != null && betaAccess.isBetaUser(viewerId));
    }

    private boolean canWrite(UUID viewerId) {
        return viewerId != null && (config.getBoolean(BusinessConfigKey.POST_USER_WRITE_ENABLED)
                || betaAccess.isBetaUser(viewerId));
    }

    /** The switch only hides the editor in the app; refusing here is what actually turns it off. */
    private void requireWrite(UUID userId) {
        if (!canWrite(userId)) {
            throw new BusinessException(ErrorConstant.POST_WRITE_NOT_ALLOWED, "Writing posts is not available yet.");
        }
    }

    private Post requireLive(UUID postId) {
        return postRepository.findById(postId)
                .filter(post -> !Boolean.TRUE.equals(post.getIsRemoved()))
                .orElseThrow(PostServiceImpl::notFound);
    }

    private Post requireOwned(UUID userId, UUID postId) {
        Post post = requireLive(postId);
        if (!post.getUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "You can only change your own posts");
        }
        return post;
    }

    private PostResponse detail(Post post, UUID viewerId) {
        return assembler.toResponse(post, View.app(viewerId, true, canWrite(viewerId)));
    }

    private SlicePageResponse<PostResponse> page(List<Post> fetched, UUID viewerId, int page, int limit) {
        boolean hasMore = fetched.size() > limit;
        List<Post> posts = hasMore ? fetched.subList(0, limit) : fetched;
        List<PostResponse> items = assembler.toResponses(posts, View.app(viewerId, false, canWrite(viewerId)));
        return SlicePageResponse.<PostResponse>builder()
                .items(items)
                .page(boundedPage(page))
                .size(limit)
                .hasMore(hasMore)
                .build();
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.NOT_FOUND, "Post not found");
    }

    private static int boundedSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private static int boundedPage(int page) {
        return Math.max(0, page);
    }
}
