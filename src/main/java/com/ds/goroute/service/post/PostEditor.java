package com.ds.goroute.service.post;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpdatePostRequest;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.event.PostPublishedEvent;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PostRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.TextModerationService;
import com.ds.goroute.service.moderation.ModerationVerdict;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationAction;
import com.ds.goroute.type.ModerationVisibility;
import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostMediaType;
import com.ds.goroute.type.PostStatus;
import com.ds.goroute.type.PostType;
import com.ds.goroute.utils.ErrorMessages;
import com.ds.goroute.utils.Mp4Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The writes of a post, shared by the app (the author acting) and the console (an admin acting for
 * a managed account). Who may act is decided by the caller; this owns what a write must satisfy,
 * whoever makes it -- the markdown media rule, the configured limits, the first-publish fan-out.
 *
 * <p>{@code actorId} is who pressed the button and lands in created_by / updated_by; the author
 * stays posts.user_id.
 */
@Component
@RequiredArgsConstructor
public class PostEditor {

    /** Storage prefix of a post's files; the post id follows. */
    public static final String STORAGE_PREFIX = "post-media/";

    /** The record name ImageStorageCleanupService knows post media under. */
    public static final String CLEANUP_ENTITY = "POST_MEDIA";

    private static final int MAX_CAPTION_LENGTH = 300;

    private final PostRepository postRepository;
    private final PlaceRepository placeRepository;
    private final ActivityRepository activityRepository;
    private final BusinessConfigService config;
    private final FileUploadService fileUploadService;
    private final TextModerationService textModerationService;
    private final ImageStorageCleanupService imageStorageCleanupService;
    private final ApplicationEventPublisher events;

    /** A new, empty draft. The trip, when given, has already been checked by the caller. */
    public Post createDraft(UUID authorId, UUID actorId, PostType postType, String title, UUID tripId) {
        if (tripId != null && postType != PostType.TRIP_STORY) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Only a trip story is about a trip");
        }
        LocalDateTime now = LocalDateTime.now();
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .userId(authorId)
                .postType(postType)
                .title(title.trim())
                .body("")
                .tripId(tripId)
                .status(PostStatus.DRAFT)
                .visibility(ContentVisibility.PUBLIC)
                .readMinutes(1)
                .likeCount(0)
                .isRemoved(false)
                .version(0)
                .createdBy(actorId)
                .updatedBy(actorId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        postRepository.insert(post);
        return post;
    }

    /** The editor's save. A stale {@code expectedVersion} changes nothing and answers 409. */
    public void update(Post post, UpdatePostRequest request, UUID actorId) {
        String body = request.getBody() == null ? "" : request.getBody();
        int maxBodyLength = config.getInt(BusinessConfigKey.POST_MAX_BODY_LENGTH);
        if (body.length() > maxBodyLength) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A post can be at most " + maxBodyLength + " characters long");
        }
        Set<UUID> liveMediaIds = postRepository.findMedia(List.of(post.getId())).stream()
                .map(PostMedia::getId)
                .collect(Collectors.toSet());
        requireOwnMedia(body, liveMediaIds);
        if (request.getCoverMediaId() != null && !liveMediaIds.contains(request.getCoverMediaId())) {
            throw new BusinessException(ErrorConstant.POST_INVALID_MEDIA_REF,
                    "The cover must be one of this post's photos or videos");
        }
        List<UUID> placeIds = placesOf(post, body, request.getPlaceIds());

        String excerpt = request.getExcerpt() == null || request.getExcerpt().isBlank()
                ? PostMarkdown.excerpt(body)
                : request.getExcerpt().trim();
        post.setTitle(request.getTitle().trim());
        post.setExcerpt(excerpt);
        post.setBody(body);
        post.setCoverMediaId(request.getCoverMediaId());
        if (request.getVisibility() != null) {
            post.setVisibility(request.getVisibility());
        }
        post.setReadMinutes(PostMarkdown.readMinutes(body));
        // "Edited" means changed after readers could see it; a draft being written is not edited.
        if (post.getPublishedAt() != null) {
            post.setEditedAt(LocalDateTime.now());
        }
        post.setUpdatedBy(actorId);
        post.setVersion(request.getExpectedVersion());
        if (postRepository.updateContent(post) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This post was changed somewhere else. Reload it and try again.");
        }
        postRepository.replacePlaces(post.getId(), placeIds);
    }

    /**
     * Publishes the post. Only the first publish -- the one that sets published_at -- tells the
     * author's followers; an unpublish and a later publish do not announce it again.
     */
    public void publish(Post post, UUID actorId) {
        if (post.getTitle() == null || post.getTitle().isBlank()
                || post.getBody() == null || post.getBody().isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A post needs a title and some text before it can be published");
        }
        if (postRepository.publishFirstTime(post.getId(), actorId)) {
            events.publishEvent(new PostPublishedEvent(post.getId(), post.getUserId()));
            return;
        }
        // Published before: back to PUBLISHED, published_at untouched. Already published is a no-op.
        postRepository.updateStatus(post.getId(), PostStatus.PUBLISHED, actorId);
    }

    /** Back to DRAFT. published_at is kept, so publishing again is not a first publish. */
    public void unpublish(Post post, UUID actorId) {
        postRepository.updateStatus(post.getId(), PostStatus.DRAFT, actorId);
    }

    /**
     * Soft delete, as a check-in. The media rows go too, which hands their files to the storage
     * sweep -- unless another row (the check-in or memory a trip story borrowed it from) still
     * names the same URL.
     */
    public void remove(Post post, UUID actorId) {
        // Collected while the rows are still readable; deletes nothing itself (POST_MEDIA is not a
        // deleted-with-record entity) but records what the sweep will reclaim.
        imageStorageCleanupService.deleteImagesForEntityRecord(CLEANUP_ENTITY, post.getId());
        if (postRepository.markRemoved(post.getId(), post.getUserId(), actorId) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Post not found");
        }
        postRepository.softDeleteAllMedia(post.getId());
    }

    /**
     * Stores one photo, or one video with its poster, under post-media/{postId}/ and adds it to the
     * post. Does not touch the post row: no version bump, no cover change (the client sets the
     * cover with its next save). Not transactional on purpose -- the upload must not hold a
     * database connection -- so an upload whose row then fails is left to the orphan sweep.
     */
    public PostMedia uploadMedia(Post post, UUID actorId, MultipartFile file, MultipartFile poster,
                                 PostMediaRole role, String caption, Integer clientDurationSeconds) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A file is required");
        }
        int maxMedia = config.getInt(BusinessConfigKey.POST_MAX_MEDIA);
        if (postRepository.countMedia(post.getId()) >= maxMedia) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A post can carry at most " + maxMedia + " photos and videos");
        }
        String cleanCaption = moderateCaption(caption, actorId);
        String prefix = STORAGE_PREFIX + post.getId();

        PostMedia.PostMediaBuilder media = PostMedia.builder()
                .id(UUID.randomUUID())
                .postId(post.getId())
                .role(role == null ? PostMediaRole.BODY : role)
                .caption(cleanCaption)
                .uploadedBy(actorId)
                .createdAt(LocalDateTime.now());

        if (isVideo(file)) {
            if (poster == null || poster.isEmpty()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A video needs its poster frame");
            }
            // Read from the file's own header before anything is stored, like a check-in video.
            // WEBM has no header we read; the app's own measurement is used for it.
            Integer durationSeconds = readDurationSeconds(file);
            if (durationSeconds == null) {
                durationSeconds = clientDurationSeconds;
            }
            int maxSeconds = config.getInt(BusinessConfigKey.POST_MAX_VIDEO_SECONDS);
            if (durationSeconds != null && durationSeconds > maxSeconds) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "A post video can be at most " + maxSeconds + " seconds long");
            }
            String posterUrl = uploadImage(actorId, prefix, poster);
            String videoUrl = fileUploadService.uploadVideo(prefix, file);
            media.mediaType(PostMediaType.VIDEO).url(videoUrl).thumbnailUrl(posterUrl).durationSeconds(durationSeconds);
        } else {
            media.mediaType(PostMediaType.IMAGE).url(uploadImage(actorId, prefix, file));
        }

        PostMedia stored = media.build();
        postRepository.insertMedia(stored);
        return postRepository.findMediaById(stored.getId()).orElse(stored);
    }

    /** Soft-deletes one media, refused while the body or the cover still shows it. */
    public void deleteMedia(Post post, UUID mediaId) {
        PostMedia media = postRepository.findMediaById(mediaId)
                .filter(found -> post.getId().equals(found.getPostId()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Media not found"));
        if (PostMarkdown.referencedMediaIds(post.getBody()).contains(media.getId())
                || media.getId().equals(post.getCoverMediaId())) {
            throw new BusinessException(ErrorConstant.POST_MEDIA_IN_USE,
                    "This photo or video is still used by the post");
        }
        postRepository.softDeleteMedia(post.getId(), media.getId());
    }

    /**
     * Adds a media row pointing at a file that already exists (a trip story's check-in photo or trip
     * memory). The file is shared, never copied: the storage sweep keeps it while any row names it.
     */
    public PostMedia attachExisting(UUID postId, UUID actorId, PostMediaType mediaType, PostMediaRole role,
                                    String url, String thumbnailUrl, Integer durationSeconds) {
        PostMedia media = PostMedia.builder()
                .id(UUID.randomUUID())
                .postId(postId)
                .mediaType(mediaType)
                .role(role)
                .url(url)
                .thumbnailUrl(thumbnailUrl)
                .durationSeconds(durationSeconds)
                .uploadedBy(actorId)
                .createdAt(LocalDateTime.now())
                .build();
        postRepository.insertMedia(media);
        return media;
    }

    /**
     * The editor's "add from the trip": the same shared row as {@link #attachExisting}, but once per
     * file (asking again for a photo already on the post answers that row) and within POST.MAX_MEDIA.
     */
    public PostMedia attachShared(Post post, UUID actorId, PostMediaType mediaType, String url,
                                  String thumbnailUrl, Integer durationSeconds) {
        List<PostMedia> live = postRepository.findMedia(List.of(post.getId()));
        for (PostMedia media : live) {
            if (url.equals(media.getUrl())) {
                return media;
            }
        }
        int maxMedia = config.getInt(BusinessConfigKey.POST_MAX_MEDIA);
        if (live.size() >= maxMedia) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A post can carry at most " + maxMedia + " photos and videos");
        }
        return attachExisting(post.getId(), actorId, mediaType, PostMediaRole.BODY, url, thumbnailUrl, durationSeconds);
    }

    /**
     * Soft-deletes every media row of the post ("start over" of a trip story). Shared files stay
     * with the check-ins and memories that own them; uploads go to the storage sweep.
     */
    public void clearMedia(Post post) {
        imageStorageCleanupService.deleteImagesForEntityRecord(CLEANUP_ENTITY, post.getId());
        postRepository.softDeleteAllMedia(post.getId());
    }

    /** Writes generated content straight to a draft (the trip-story generator), bypassing the editor's checks. */
    public void writeGenerated(Post post, String body, UUID coverMediaId, List<UUID> placeIds, UUID actorId) {
        post.setBody(body);
        post.setExcerpt(PostMarkdown.excerpt(body));
        post.setReadMinutes(PostMarkdown.readMinutes(body));
        post.setCoverMediaId(coverMediaId);
        post.setUpdatedBy(actorId);
        if (postRepository.updateContent(post) != 1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The draft was changed while it was written");
        }
        post.setVersion(post.getVersion() + 1);
        postRepository.replacePlaces(post.getId(), placeIds);
    }

    private void requireOwnMedia(String body, Set<UUID> liveMediaIds) {
        if (!PostMarkdown.hasOnlyOwnMedia(body, liveMediaIds)) {
            throw new BusinessException(ErrorConstant.POST_INVALID_MEDIA_REF,
                    "Photos and videos in a post must be ones uploaded to this post");
        }
    }

    /**
     * The places a post is about, in reading order: first what the body itself points at -- the
     * catalogue place of every {@code activity:} stop of the post's trip, then every {@code place:}
     * heading -- then whatever the client still sends as {@code placeIds} (the console's picker).
     * One source of truth, so the list under the post never disagrees with the story above it.
     * Body-derived places past POST.MAX_PLACES are dropped rather than refused: a long itinerary
     * must still save.
     */
    List<UUID> placesOf(Post post, String body, List<UUID> requested) {
        LinkedHashSet<UUID> placeIds = new LinkedHashSet<>();
        Set<UUID> activityIds = PostMarkdown.referencedActivityIds(body);
        if (post.getTripId() != null && !activityIds.isEmpty()) {
            // Only this trip's stops count: a stop id of another trip names nothing here.
            activityRepository.findByTripId(post.getTripId()).stream()
                    .filter(activity -> activityIds.contains(activity.getId()))
                    .map(Activity::catalogPlaceId)
                    .filter(Objects::nonNull)
                    .forEach(placeIds::add);
        }
        placeIds.addAll(PostMarkdown.referencedPlaceIds(body));
        int maxPlaces = config.getInt(BusinessConfigKey.POST_MAX_PLACES);
        List<UUID> derived = placeIds.stream().limit(maxPlaces).toList();
        List<UUID> extra = requested == null ? List.of() : requested.stream()
                .filter(Objects::nonNull)
                .filter(id -> !placeIds.contains(id))
                .distinct()
                .toList();
        if (derived.size() + extra.size() > maxPlaces) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A post can be tagged with at most " + maxPlaces + " places");
        }
        List<UUID> all = new ArrayList<>(derived);
        all.addAll(extra);
        requireCataloguePlaces(all);
        return all;
    }

    /** Every id names a catalogue place; a stale {@code place:} heading or picker id is refused. */
    private void requireCataloguePlaces(List<UUID> placeIds) {
        if (placeIds.isEmpty()) {
            return;
        }
        Map<UUID, Place> found = placeRepository.findCardsByIds(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, place -> place, (first, second) -> first));
        if (found.size() != placeIds.size()) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found");
        }
    }

    /** A caption is public text under a photo: the same filter as the body, at the same level. */
    private String moderateCaption(String caption, UUID actorId) {
        if (caption == null || caption.isBlank()) {
            return null;
        }
        String trimmed = caption.trim();
        if (trimmed.length() > MAX_CAPTION_LENGTH) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A caption can be at most " + MAX_CAPTION_LENGTH + " characters long");
        }
        ModerationVerdict verdict = textModerationService.evaluate(ModeratedContentType.POST, "caption",
                ModerationVisibility.PUBLIC, trimmed, actorId);
        if (verdict.action() == ModerationAction.BLOCK) {
            String message = verdict.category() == null
                    ? ErrorMessages.of(ErrorConstant.CONTENT_BLOCKED_BY_MODERATION)
                    : ErrorMessages.of("moderation.category." + verdict.category().name());
            throw new BusinessException(ErrorConstant.CONTENT_BLOCKED_BY_MODERATION, message);
        }
        return trimmed;
    }

    /** Through the single upload door: size, type and image moderation. */
    private String uploadImage(UUID actorId, String prefix, MultipartFile file) {
        ImageUploadOutcome outcome = fileUploadService.uploadImage(ImageUploadRequest.of(
                actorId, ImageUploadRequest.ImageEntryPoint.POST_MEDIA, prefix), file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection()
                            ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION
                            : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }
        return outcome.url();
    }

    private static boolean isVideo(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("video/");
    }

    private static Integer readDurationSeconds(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            return Mp4Duration.seconds(input);
        } catch (IOException exception) {
            return null;
        }
    }
}
