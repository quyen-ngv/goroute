package com.ds.goroute.service.marketplace;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.entity.Trip;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.persistence.LocalEventRepository;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.service.MeetupService;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.TripRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.FoodService;
import com.ds.goroute.type.ChatShareType;
import com.ds.goroute.type.ContentVisibility;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.TripVisibility;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Checks the one attachment of a {@code SHARE} message: a reference to something public.
 *
 * <p>The attachment is {@code {shareType, targetId}}, plus {@code citySlug} for a dish. Only the
 * reference is stored — each reader's app loads the item through its own detail endpoint — so
 * this is not about what the card shows. It refuses a share nobody in the thread could open: a
 * private trip, a hidden check-in, a draft event, a meetup still waiting for review. Membership
 * of a chat never grants access to what is shared into it.
 *
 * <p>The two services are looked up per call, not injected: the meetup service reaches the chat
 * service through its group chat, and the chat service depends on this validator, so a direct
 * dependency is a construction cycle that stops the application from starting.
 */
@Component
@RequiredArgsConstructor
public class ChatShareValidator {

    static final String SHARE_TYPE = "shareType";
    static final String TARGET_ID = "targetId";
    static final String CITY_SLUG = "citySlug";
    private static final Set<String> KEYS = Set.of(SHARE_TYPE, TARGET_ID, CITY_SLUG);
    private static final Pattern SLUG = Pattern.compile("[a-z0-9-]{1,64}");

    private final PlaceRepository places;
    private final ObjectProvider<FoodService> foods;
    private final UserCheckinRepository checkins;
    private final ContentModerationService moderation;
    private final TripRepository trips;
    private final LocalEventRepository events;
    private final ObjectProvider<MeetupService> meetups;

    /** @return the one attachment to store, normalised */
    public List<Map<String, Object>> validate(UUID senderId, List<Map<String, Object>> attachments) {
        if (attachments == null || attachments.size() != 1 || attachments.get(0) == null) {
            throw bad("A share carries exactly one item");
        }
        Map<String, Object> share = attachments.get(0);
        for (String key : share.keySet()) {
            if (!KEYS.contains(key)) throw bad("Unsupported share field: " + key);
        }
        ChatShareType type = shareType(share.get(SHARE_TYPE));
        UUID targetId = uuid(share.get(TARGET_ID));
        String citySlug = type == ChatShareType.FOOD ? citySlug(share.get(CITY_SLUG)) : null;
        if (type != ChatShareType.FOOD && share.get(CITY_SLUG) != null) {
            throw bad("citySlug belongs to a dish share only");
        }

        requireShareable(senderId, type, targetId, citySlug);

        Map<String, Object> clean = new LinkedHashMap<>();
        clean.put(SHARE_TYPE, type.name());
        clean.put(TARGET_ID, targetId.toString());
        if (citySlug != null) clean.put(CITY_SLUG, citySlug);
        return List.of(clean);
    }

    /**
     * Whether {@code viewerId} could share this item, for cards Pengoo puts under an answer:
     * the same rule as a person's share, but a refusal is a quiet {@code false}.
     */
    public boolean isShareable(UUID viewerId, ChatShareType type, UUID id, String citySlug) {
        if (type == null || id == null) return false;
        if (type == ChatShareType.FOOD && (citySlug == null || !SLUG.matcher(citySlug).matches())) return false;
        try {
            requireShareable(viewerId, type, id, type == ChatShareType.FOOD ? citySlug : null);
            return true;
        } catch (BusinessException notShareable) {
            return false;
        }
    }

    private void requireShareable(UUID senderId, ChatShareType type, UUID id, String citySlug) {
        boolean isShareable = switch (type) {
            case PLACE -> places.findById(id).isPresent();
            // Throws its own not-found when the dish is not public in that city.
            case FOOD -> foods.getObject().getDetail(id, citySlug) != null;
            case CHECKIN -> checkins.findById(id).filter(this::isPublicCheckin).isPresent();
            case TRIP -> trips.findById(id).filter(this::isPublicTrip).isPresent();
            case EVENT -> events.findPublishedById(id).isPresent();
            case MEETUP -> isApprovedMeetup(senderId, id);
        };
        if (!isShareable) throw new BusinessException(ErrorConstant.NOT_FOUND, "This item cannot be shared");
    }

    private boolean isPublicCheckin(UserCheckin checkin) {
        return !Boolean.TRUE.equals(checkin.getIsRemoved())
                && checkin.getVisibility() == ContentVisibility.PUBLIC
                && !moderation.isTakenDown(ModeratedContentType.CHECKIN, checkin.getId());
    }

    private boolean isPublicTrip(Trip trip) {
        return !Boolean.TRUE.equals(trip.getIsDeleted()) && trip.getVisibility() == TripVisibility.PUBLIC;
    }

    /** Asked as the sender, so the meetup feature flag, blocks and take-downs all apply. */
    private boolean isApprovedMeetup(UUID senderId, UUID meetupId) {
        MeetupDetailResponse meetup = meetups.getObject().detail(senderId, meetupId);
        return meetup != null && meetup.getStatus() == MeetupStatus.APPROVED;
    }

    private ChatShareType shareType(Object value) {
        if (value == null) throw bad("shareType is required");
        try {
            return ChatShareType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException unknown) {
            throw bad("Unsupported shareType: " + value);
        }
    }

    private UUID uuid(Object value) {
        if (value == null) throw bad("targetId is required");
        try {
            return UUID.fromString(String.valueOf(value));
        } catch (IllegalArgumentException malformed) {
            throw bad("targetId is not valid");
        }
    }

    private String citySlug(Object value) {
        String slug = value == null ? null : String.valueOf(value).trim();
        if (slug == null || !SLUG.matcher(slug).matches()) throw bad("citySlug is required for a dish share");
        return slug;
    }

    private BusinessException bad(String message) {
        return new BusinessException(ErrorConstant.BAD_REQUEST, message);
    }
}
