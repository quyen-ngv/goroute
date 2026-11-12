package com.ds.goroute.service.post;

import com.ds.goroute.dto.response.PostAuthorResponse;
import com.ds.goroute.dto.response.PostMediaResponse;
import com.ds.goroute.dto.response.PostPlaceResponse;
import com.ds.goroute.dto.response.PostResponse;
import com.ds.goroute.dto.response.PostStopResponse;
import com.ds.goroute.dto.response.PostTripResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.entity.PostPlaceRef;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.User;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.ContentCommentRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PostRepository;
import com.ds.goroute.repository.SavedPostRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.TripAccessGuard;
import com.ds.goroute.service.UserGuideService;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.SavedPostType;
import com.ds.goroute.type.TripVisibility;
import com.ds.goroute.utils.PlaceDisplayRating;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns posts into {@link PostResponse}s, a page at a time: one query each for the authors, their
 * guide badges, the media, the places, the likes, the saves, the comment counts and the takedowns,
 * whatever the page size. A twenty-post feed page is never twenty-times anything.
 */
@Component
@RequiredArgsConstructor
public class PostResponseAssembler {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final UserGuideService userGuideService;
    private final PlaceRepository placeRepository;
    private final TripRepository tripRepository;
    private final SavedPostRepository savedPostRepository;
    private final ContentCommentRepository contentCommentRepository;
    private final ContentModerationService contentModerationService;
    private final ActivityRepository activityRepository;
    private final TripAccessGuard tripAccessGuard;

    /** Who is looking, and what the answer may say. */
    public record View(UUID viewerId, boolean includeBody, boolean consoleView, boolean viewerCanWrite) {

        public static View app(UUID viewerId, boolean includeBody, boolean viewerCanWrite) {
            return new View(viewerId, includeBody, false, viewerCanWrite);
        }

        /** The console: reads drafts and every trip, and adds {@code hidden} / {@code isManagedAuthor}. */
        public static View console(boolean includeBody) {
            return new View(null, includeBody, true, false);
        }
    }

    public PostResponse toResponse(Post post, View view) {
        return toResponses(List.of(post), view).get(0);
    }

    public List<PostResponse> toResponses(List<Post> posts, View view) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<UUID> postIds = posts.stream().map(Post::getId).toList();
        Set<UUID> authorIds = posts.stream().map(Post::getUserId).collect(Collectors.toCollection(LinkedHashSet::new));

        Map<UUID, User> authors = userRepository.findByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
        Map<UUID, String> guideTitles = userGuideService.activeGuideTitles(authorIds);
        Map<UUID, List<PostMedia>> media = postRepository.findMedia(postIds).stream()
                .collect(Collectors.groupingBy(PostMedia::getPostId));
        Map<UUID, List<PostPlaceRef>> placeRefs = postRepository.findPlaces(postIds).stream()
                .collect(Collectors.groupingBy(PostPlaceRef::getPostId));
        Map<UUID, Place> places = loadPlaces(placeRefs.values());
        Map<UUID, Trip> trips = loadTrips(posts);
        Set<UUID> likedIds = postRepository.findLikedIds(view.viewerId(), postIds);
        Set<UUID> savedIds = savedPostRepository.findSavedIds(view.viewerId(), SavedPostType.POST, postIds);
        Set<UUID> takenDown = contentModerationService.takenDownIds(ModeratedContentType.POST, postIds);
        // GET /content-comments/count only opens the thread of a published public post that is
        // not taken down; any other post counts 0 here, as for a check-in.
        List<UUID> commentable = posts.stream()
                .filter(Post::isPubliclyReadable)
                .map(Post::getId)
                .filter(id -> !takenDown.contains(id))
                .toList();
        Map<UUID, Integer> commentCounts = commentable.isEmpty() ? Map.of()
                : contentCommentRepository.countActiveByTargets(ModeratedContentType.POST, commentable);

        return posts.stream()
                .map(post -> build(post, view, authors.get(post.getUserId()),
                        guideTitles.containsKey(post.getUserId()), guideTitles.get(post.getUserId()),
                        media.getOrDefault(post.getId(), List.of()),
                        placeRefs.getOrDefault(post.getId(), List.of()), places,
                        trips.get(post.getTripId()),
                        likedIds.contains(post.getId()), savedIds.contains(post.getId()),
                        commentCounts.getOrDefault(post.getId(), 0), takenDown.contains(post.getId())))
                .toList();
    }

    private PostResponse build(Post post, View view, User author, boolean isGuide, String guideTitle,
                               List<PostMedia> media, List<PostPlaceRef> placeRefs, Map<UUID, Place> places,
                               Trip trip, boolean hasLiked, boolean hasSaved, int commentCount,
                               boolean isTakenDown) {
        List<PostMediaResponse> mediaResponses = media.stream().map(PostResponseAssembler::toMedia).toList();
        PostMediaResponse cover = post.getCoverMediaId() == null ? null : mediaResponses.stream()
                .filter(item -> item.getId().equals(post.getCoverMediaId()))
                .findFirst()
                .orElse(null);
        boolean isOwn = view.viewerId() != null && view.viewerId().equals(post.getUserId());
        boolean isManagedAuthor = author != null && Boolean.TRUE.equals(author.getManagedByAdmin());
        boolean isLive = !Boolean.TRUE.equals(post.getIsRemoved());

        return PostResponse.builder()
                .id(post.getId())
                .postType(post.getPostType())
                .title(post.getTitle())
                .excerpt(post.getExcerpt())
                .body(view.includeBody() ? post.getBody() : null)
                .status(post.getStatus())
                .visibility(post.getVisibility())
                .readMinutes(post.getReadMinutes() == null ? 1 : post.getReadMinutes())
                .cover(cover)
                .media(mediaResponses)
                .places(placeRefs.stream()
                        .map(ref -> places.get(ref.getPlaceId()))
                        .filter(Objects::nonNull)
                        .map(PostResponseAssembler::toPlace)
                        .toList())
                .trip(visibleTrip(trip, isOwn, view))
                .stops(stopsOf(post, trip, isOwn, view))
                .author(toAuthor(post.getUserId(), author, isGuide, guideTitle))
                .publishedAt(post.getPublishedAt())
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .edited(post.getEditedAt() != null)
                .likeCount(post.getLikeCount() == null ? 0 : post.getLikeCount())
                .hasLiked(hasLiked)
                .hasSaved(hasSaved)
                .commentCount(commentCount)
                .isOwn(isOwn)
                // The console edits only what it still holds; the app, the author's own live post
                // while writing is open to them.
                .canEdit(isLive && (view.consoleView() ? isManagedAuthor : isOwn && view.viewerCanWrite()))
                .version(post.getVersion() == null ? 0 : post.getVersion())
                .hidden(view.consoleView() ? isTakenDown : null)
                .isManagedAuthor(view.consoleView() ? isManagedAuthor : null)
                .build();
    }

    /**
     * A trip is named only where the reader could open it: public or link-shared, or the reader is
     * the author. A private trip's name stays private even when a public story is about it.
     */
    private PostTripResponse visibleTrip(Trip trip, boolean isOwn, View view) {
        if (trip == null || Boolean.TRUE.equals(trip.getIsDeleted())) {
            return null;
        }
        boolean isOpen = trip.getVisibility() == TripVisibility.PUBLIC || trip.getVisibility() == TripVisibility.SHARED;
        if (!isOpen && !isOwn && !view.consoleView()) {
            return null;
        }
        return PostTripResponse.builder()
                .id(trip.getId())
                .name(trip.getName())
                .startDate(trip.getStartDate())
                .endDate(trip.getEndDate())
                .canEdit(view.viewerId() != null && tripAccessGuard.canEdit(trip, view.viewerId()))
                .build();
    }

    /**
     * The stops behind a trip story's {@code activity:} headings, read live. Detail only (the body
     * is what points at them). The author and the console get every live stop -- the editor needs
     * the ones the story does not mention yet; a reader gets the ones the body names, whatever the
     * trip's visibility: the author chose to write about them.
     */
    private List<PostStopResponse> stopsOf(Post post, Trip trip, boolean isOwn, View view) {
        if (!view.includeBody() || post.getTripId() == null || trip == null
                || Boolean.TRUE.equals(trip.getIsDeleted())) {
            return null;
        }
        Set<UUID> referenced = PostMarkdown.referencedActivityIds(post.getBody());
        boolean everyStop = isOwn || view.consoleView();
        if (!everyStop && referenced.isEmpty()) {
            return List.of();
        }
        return activityRepository.findByTripId(trip.getId()).stream()
                .filter(activity -> activity.getStatus() != ActivityStatus.REJECTED)
                .filter(activity -> everyStop || referenced.contains(activity.getId()))
                .map(PostResponseAssembler::toStop)
                .toList();
    }

    private static PostStopResponse toStop(Activity activity) {
        return PostStopResponse.builder()
                .activityId(activity.getId())
                .dayNumber(activity.getDayNumber())
                .sortOrder(activity.getSortOrder())
                .name(activity.getName())
                .address(activity.getAddress())
                .endAddress(activity.getEndAddress())
                .lat(activity.getLat())
                .lng(activity.getLng())
                .startTime(activity.getStartTime())
                .endTime(activity.getEndTime())
                .category(activity.getCategory())
                .transportMode(activity.getTransportMode() == null ? null : activity.getTransportMode().name())
                .placeId(activity.catalogPlaceId())
                .photoUrl(activity.getPhotoUrl())
                .isAccommodation(Boolean.TRUE.equals(activity.getIsAccommodation()))
                .isStartingPoint(Boolean.TRUE.equals(activity.getIsStartingPoint()))
                .build();
    }

    private static PostAuthorResponse toAuthor(UUID userId, User author, boolean isGuide, String guideTitle) {
        return PostAuthorResponse.builder()
                .userId(userId)
                .displayName(author == null ? null : author.getFullName())
                .username(author == null ? null : author.getUsername())
                .avatarUrl(author == null ? null : author.getAvatarUrl())
                .guide(isGuide)
                .guideTitle(isGuide ? guideTitle : null)
                .build();
    }

    public static PostMediaResponse toMedia(PostMedia media) {
        return PostMediaResponse.builder()
                .id(media.getId())
                .mediaType(media.getMediaType())
                .role(media.getRole())
                .url(media.getUrl())
                .thumbnailUrl(media.getThumbnailUrl())
                .durationSeconds(media.getDurationSeconds())
                .caption(media.getCaption())
                .build();
    }

    private static PostPlaceResponse toPlace(Place place) {
        return PostPlaceResponse.builder()
                .placeId(place.getId())
                .name(place.getTitle())
                .address(place.getAddress())
                .thumbnail(place.getThumbnail())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .rating(PlaceDisplayRating.of(place))
                .placeGroup(place.getPlaceGroup())
                .build();
    }

    private Map<UUID, Place> loadPlaces(Collection<List<PostPlaceRef>> refs) {
        List<UUID> placeIds = refs.stream()
                .flatMap(List::stream)
                .map(PostPlaceRef::getPlaceId)
                .distinct()
                .toList();
        if (placeIds.isEmpty()) {
            return Map.of();
        }
        return placeRepository.findCardsByIds(placeIds).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity(), (first, second) -> first));
    }

    /** Trip stories are a minority of any page; the distinct trips are read one by one. */
    private Map<UUID, Trip> loadTrips(List<Post> posts) {
        Map<UUID, Trip> trips = new HashMap<>();
        posts.stream()
                .map(Post::getTripId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(tripId -> tripRepository.findById(tripId).ifPresent(trip -> trips.put(tripId, trip)));
        return trips;
    }
}
