package com.ds.goroute.service.post;

import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.Post;
import com.ds.goroute.entity.PostMedia;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.post.TripStoryMediaCollector.Item;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PostMediaRole;
import com.ds.goroute.type.PostMediaType;
import com.ds.goroute.type.PostType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes the first draft of "a post about this trip" (.docs/BLOG_POSTS_PLAN_2026-11-10.md §7) from
 * the itinerary and what the author themself posted during it.
 *
 * <p>Every day is a {@code ## [Ngày N · dd/MM](day:N)} heading and every stop a
 * {@code ### [name](activity:<id>)} heading, so the app draws them as live day headers and stop
 * cards that follow the trip. Under each stop come the author's check-in captions and <em>every</em>
 * photo of theirs at that stop as one gallery block (the author removes what they do not want;
 * {@link TripStoryMediaCollector} decides which photo belongs to which stop). Photos that matched
 * no stop go at the end of the day they were taken, and after the last day when no day fits. Photos
 * are new media rows that point at the same files, never copies. A stop with nothing but its name
 * still gets its heading: the draft is an outline the author then writes into.
 */
@Component
@RequiredArgsConstructor
public class TripStoryDraftBuilder {

    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private final PostEditor editor;
    private final ActivityRepository activityRepository;
    private final TripStoryMediaCollector mediaCollector;
    private final UserRepository userRepository;
    private final BusinessConfigService config;

    /** Creates the draft for this author and trip (access already checked) and fills it. */
    public Post build(Trip trip, UUID authorId) {
        Post post = editor.createDraft(authorId, authorId, PostType.TRIP_STORY, titleOf(trip), trip.getId());
        fill(post, trip, authorId);
        return post;
    }

    /**
     * "Start over": the post becomes the draft it would be written as today -- title, body, cover
     * and every borrowed photo -- as if the author had never touched it. The media rows are
     * replaced; the files they point at are shared with the check-ins and stay.
     */
    public void reset(Post post, Trip trip, UUID authorId) {
        editor.clearMedia(post);
        post.setTitle(titleOf(trip));
        fill(post, trip, authorId);
    }

    private static String titleOf(Trip trip) {
        String title = trip.getName() == null || trip.getName().isBlank() ? "Trip" : trip.getName().trim();
        return title.length() > 200 ? title.substring(0, 200) : title;
    }

    private void fill(Post post, Trip trip, UUID authorId) {
        // Itinerary order (day, start time, sort order); a rejected suggestion was never part of it.
        List<Activity> activities = activityRepository.findByTripId(trip.getId()).stream()
                .filter(activity -> activity.getStatus() != ActivityStatus.REJECTED)
                .toList();

        int maxPlaces = config.getInt(BusinessConfigKey.POST_MAX_PLACES);
        List<UUID> placeIds = activities.stream()
                .map(Activity::catalogPlaceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .limit(maxPlaces)
                .toList();

        Map<UUID, List<Item>> mediaByActivity = TripStoryMediaCollector.byActivity(
                mediaCollector.collect(trip, authorId, activities));

        boolean isVietnamese = userRepository.findById(authorId)
                .map(user -> user.getLanguage() != null && user.getLanguage().toLowerCase().startsWith("vi"))
                .orElse(false);
        int maxBodyLength = config.getInt(BusinessConfigKey.POST_MAX_BODY_LENGTH);
        Budget budget = new Budget(config.getInt(BusinessConfigKey.POST_MAX_MEDIA));

        // The cover is the trip's own cover, else the first photo of the story.
        if (trip.getCoverImageUrl() != null && !trip.getCoverImageUrl().isBlank() && budget.left > 0) {
            budget.cover = editor.attachExisting(post.getId(), authorId, PostMediaType.IMAGE, PostMediaRole.COVER,
                    trip.getCoverImageUrl(), null, null).getId();
            budget.left--;
        }

        Map<Integer, List<Activity>> byDay = new TreeMap<>();
        activities.forEach(activity -> byDay
                .computeIfAbsent(activity.getDayNumber() == null ? 1 : activity.getDayNumber(), day -> new ArrayList<>())
                .add(activity));
        // Photos that matched no stop, by the day they were taken (null: no day of the trip).
        Map<Integer, List<Item>> looseByDay = new TreeMap<>();
        List<Item> looseAnyDay = new ArrayList<>();
        for (Item item : mediaByActivity.getOrDefault(TripStoryMediaCollector.NO_ACTIVITY, List.of())) {
            Integer day = dayOf(trip, item);
            if (day != null && byDay.containsKey(day)) {
                looseByDay.computeIfAbsent(day, key -> new ArrayList<>()).add(item);
            } else {
                looseAnyDay.add(item);
            }
        }

        StringBuilder body = new StringBuilder();
        Set<String> usedUrls = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<Activity>> day : byDay.entrySet()) {
            appendWithin(body, dayHeading(day.getKey(), trip.getStartDate(), isVietnamese), maxBodyLength);
            for (Activity activity : day.getValue()) {
                appendWithin(body, PostMarkdown.stopHeading(activityName(activity), activity.getId()), maxBodyLength);
                appendStopMedia(body, post, authorId, mediaByActivity.getOrDefault(activity.getId(), List.of()),
                        usedUrls, budget, maxBodyLength);
            }
            appendStopMedia(body, post, authorId, looseByDay.getOrDefault(day.getKey(), List.of()),
                    usedUrls, budget, maxBodyLength);
        }
        appendStopMedia(body, post, authorId, looseAnyDay, usedUrls, budget, maxBodyLength);

        editor.writeGenerated(post, body.toString().strip(), budget.cover, placeIds, authorId);
    }

    /** The author's words about the stop (each check-in's caption once), then every photo as one gallery. */
    private void appendStopMedia(StringBuilder body, Post post, UUID authorId, List<Item> items,
                                 Set<String> usedUrls, Budget budget, int maxBodyLength) {
        Set<String> captions = new LinkedHashSet<>();
        for (Item item : items) {
            if (item.caption() != null) {
                captions.add(plainCaption(item.caption()));
            }
        }
        for (String caption : captions) {
            appendWithin(body, caption, maxBodyLength);
        }
        List<UUID> gallery = new ArrayList<>();
        for (Item item : items) {
            if (budget.left <= 0) {
                break;
            }
            if (!usedUrls.add(item.url())) {
                continue;
            }
            PostMedia media = editor.attachExisting(post.getId(), authorId, item.mediaType(),
                    PostMediaRole.BODY, item.url(), item.thumbnailUrl(), item.durationSeconds());
            gallery.add(media.getId());
            budget.left--;
            if (budget.cover == null) {
                budget.cover = media.getId();
            }
        }
        if (!gallery.isEmpty()) {
            appendWithin(body, PostMarkdown.galleryBlock(gallery), maxBodyLength);
        }
    }

    /** What is left of POST.MAX_MEDIA, and the cover chosen so far. */
    private static final class Budget {
        int left;
        UUID cover;

        Budget(int left) {
            this.left = left;
        }
    }

    /** The trip day a photo was taken on, 1-based, or null when the trip has no dates or it falls outside them. */
    private static Integer dayOf(Trip trip, Item item) {
        if (trip.getStartDate() == null || item.takenAt() == null) {
            return null;
        }
        long day = ChronoUnit.DAYS.between(trip.getStartDate(), item.takenAt().toLocalDate()) + 1;
        return day < 1 ? null : (int) day;
    }

    private static String dayHeading(int dayNumber, LocalDate startDate, boolean isVietnamese) {
        String label = (isVietnamese ? "Ngày " : "Day ") + dayNumber;
        if (startDate != null) {
            label += " · " + startDate.plusDays(Math.max(0, dayNumber - 1L)).format(DAY_MONTH);
        }
        return PostMarkdown.dayHeading(label, dayNumber);
    }

    private static String activityName(Activity activity) {
        if (activity.getName() != null && !activity.getName().isBlank()) {
            return activity.getName().trim();
        }
        return activity.getAddress() != null && !activity.getAddress().isBlank() ? activity.getAddress().trim() : "…";
    }

    /**
     * A caption is the author's plain words. Its "![" would read as a picture that the draft never
     * uploaded and fail the next save, so it is broken apart.
     */
    private static String plainCaption(String caption) {
        return caption.trim().replace("![", "! [");
    }

    /**
     * Adds one block when it still fits under POST.MAX_BODY_LENGTH. A long trip loses its tail of
     * captions rather than producing a draft the author could not save.
     */
    private static boolean appendWithin(StringBuilder body, String block, int maxLength) {
        int added = block.length() + (body.isEmpty() ? 0 : 2);
        if (body.length() + added > maxLength) {
            return false;
        }
        if (!body.isEmpty()) {
            body.append("\n\n");
        }
        body.append(block);
        return true;
    }
}
