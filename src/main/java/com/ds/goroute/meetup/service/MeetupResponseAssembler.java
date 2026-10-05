package com.ds.goroute.meetup.service;

import com.ds.goroute.entity.User;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.dto.MeetupParticipantResponse;
import com.ds.goroute.meetup.dto.MeetupPersonResponse;
import com.ds.goroute.meetup.dto.MeetupSummaryResponse;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupViewerRole;
import com.ds.goroute.utils.GeoDistance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Turns meetup rows into what one viewer may see. The reveal rules live here and nowhere else:
 * the true point, place name and address go out only to the host, or for an EXACT meetup that is
 * OPEN or that the viewer JOINED; everyone else gets the displayed point and a circle. People are
 * batch-loaded once per page, never per row.
 */
@Component
@RequiredArgsConstructor
public class MeetupResponseAssembler {

    /** The circle drawn around an EXACT meetup whose spot is not revealed. */
    public static final int HIDDEN_EXACT_RADIUS_M = 300;
    static final int PREVIEW_LIMIT = 8;

    private final MeetupRepository repository;
    private final UserRepository users;

    /** A page of cards for {@code viewerId}; {@code originLat/Lng} may be null (no distance). */
    public List<MeetupSummaryResponse> summaries(List<Meetup> meetups, UUID viewerId, BigDecimal originLat,
                                                 BigDecimal originLng, LocalDateTime nowUtc) {
        if (meetups.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = meetups.stream().map(Meetup::getId).toList();
        Map<UUID, Long> joined = repository.countJoined(ids);
        Map<UUID, MeetupParticipant> viewerRows = repository.findParticipantsForUser(viewerId, ids);
        Map<UUID, User> hosts = people(meetups.stream().map(Meetup::getHostUserId).toList());
        return meetups.stream().map(meetup -> {
            MeetupSummaryResponse card = new MeetupSummaryResponse();
            fill(card, meetup, viewerId, viewerRows.get(meetup.getId()), hosts.get(meetup.getHostUserId()),
                    joined.getOrDefault(meetup.getId(), 0L), nowUtc);
            card.setDistanceKm(distance(originLat, originLng, meetup));
            return card;
        }).toList();
    }

    /** The sheet for {@code viewerId}, whose own participant row is {@code viewerRow} (may be null). */
    public MeetupDetailResponse detail(Meetup meetup, UUID viewerId, MeetupParticipant viewerRow,
                                       LocalDateTime nowUtc, int maxResubmits) {
        Map<UUID, Long> joinedCounts = repository.countJoined(List.of(meetup.getId()));
        long joined = joinedCounts.getOrDefault(meetup.getId(), 0L);
        Map<UUID, User> hosts = people(List.of(meetup.getHostUserId()));

        MeetupDetailResponse detail = new MeetupDetailResponse();
        fill(detail, meetup, viewerId, viewerRow, hosts.get(meetup.getHostUserId()), joined, nowUtc);
        boolean host = meetup.getHostUserId().equals(viewerId);
        detail.setDescription(meetup.getDescription());
        detail.setAddress(detail.isLocationHidden() ? null : meetup.getAddress());
        detail.setPlaceId(detail.isLocationHidden() ? null : meetup.getPlaceId());
        detail.setCancelReason(meetup.getCancelReason());
        detail.setDataVersion(meetup.getDataVersion());

        boolean participantsVisible = participantsVisible(meetup, viewerId, viewerRow);
        detail.setParticipantsVisible(participantsVisible);
        if (participantsVisible && joined > 0) {
            List<MeetupParticipant> rows = repository.findParticipants(
                    meetup.getId(), List.of(MeetupParticipantStatus.JOINED), PREVIEW_LIMIT);
            Map<UUID, User> people = people(rows.stream().map(MeetupParticipant::getUserId).toList());
            detail.setParticipantsPreview(rows.stream()
                    .map(row -> person(row.getUserId(), people.get(row.getUserId())))
                    .toList());
        } else {
            detail.setParticipantsPreview(List.of());
        }
        if (host) {
            detail.setPendingRequestCount(
                    repository.countParticipantsWithStatus(meetup.getId(), MeetupParticipantStatus.REQUESTED));
            detail.setRejectReason(meetup.getRejectReason());
            detail.setReviewNote(meetup.getReviewNote());
            int used = meetup.getResubmitCount() == null ? 0 : meetup.getResubmitCount();
            detail.setResubmitsLeft(Math.max(0, maxResubmits - used));
        }
        return detail;
    }

    /**
     * Participant rows as the viewer may see them: the host sees every row and the join
     * messages; a JOINED viewer sees who joined; anyone else sees who joined an OPEN meetup and
     * nobody for an APPROVAL one.
     */
    public List<MeetupParticipantResponse> participants(Meetup meetup, UUID viewerId, MeetupParticipant viewerRow,
                                                        int limit) {
        boolean host = meetup.getHostUserId().equals(viewerId);
        if (!participantsVisible(meetup, viewerId, viewerRow)) {
            return List.of();
        }
        List<MeetupParticipant> rows = repository.findParticipants(meetup.getId(),
                host ? List.of() : List.of(MeetupParticipantStatus.JOINED), limit);
        return participantResponses(rows, host);
    }

    public List<MeetupParticipantResponse> participantResponses(List<MeetupParticipant> rows, boolean withMessages) {
        Map<UUID, User> people = people(rows.stream().map(MeetupParticipant::getUserId).toList());
        return rows.stream().map(row -> participant(row, people.get(row.getUserId()), withMessages)).toList();
    }

    public MeetupParticipantResponse participant(MeetupParticipant row, User user, boolean withMessage) {
        return MeetupParticipantResponse.builder()
                .user(person(row.getUserId(), user))
                .status(row.getStatus())
                .message(withMessage ? row.getMessage() : null)
                .requestedAt(row.getRequestedAt())
                .decidedAt(row.getDecidedAt())
                .build();
    }

    public Map<UUID, User> people(Collection<UUID> userIds) {
        Set<UUID> distinct = new HashSet<>();
        for (UUID id : userIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        Map<UUID, User> byId = new HashMap<>();
        if (distinct.isEmpty()) {
            return byId;
        }
        for (User user : users.findByIds(distinct)) {
            byId.put(user.getId(), user);
        }
        return byId;
    }

    public static MeetupPersonResponse person(UUID id, User user) {
        return MeetupPersonResponse.builder()
                .id(id)
                .name(displayName(user))
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .build();
    }

    public static String displayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }

    /** Whether the true point, place name and address may be shown to this viewer. */
    public static boolean isLocationRevealed(Meetup meetup, UUID viewerId, MeetupParticipant viewerRow) {
        if (meetup.getHostUserId().equals(viewerId)) {
            return true;
        }
        if (meetup.getLocationMode() != MeetupLocationMode.EXACT) {
            return false;
        }
        return meetup.getJoinPolicy() == MeetupJoinPolicy.OPEN || isJoined(viewerRow);
    }

    public static boolean participantsVisible(Meetup meetup, UUID viewerId, MeetupParticipant viewerRow) {
        return meetup.getHostUserId().equals(viewerId)
                || isJoined(viewerRow)
                || meetup.getJoinPolicy() == MeetupJoinPolicy.OPEN;
    }

    private void fill(MeetupSummaryResponse card, Meetup meetup, UUID viewerId, MeetupParticipant viewerRow,
                      User host, long joined, LocalDateTime nowUtc) {
        boolean isHost = meetup.getHostUserId().equals(viewerId);
        boolean revealed = isLocationRevealed(meetup, viewerId, viewerRow);
        card.setId(meetup.getId());
        card.setHost(person(meetup.getHostUserId(), host));
        card.setTitle(meetup.getTitle());
        card.setCategory(meetup.getCategory());
        card.setLocationMode(meetup.getLocationMode());
        card.setLocationHidden(!revealed);
        if (revealed) {
            card.setLatitude(meetup.getLatitude());
            card.setLongitude(meetup.getLongitude());
            card.setRadiusM(meetup.getRadiusM());
            card.setPlaceName(meetup.getPlaceName());
        } else {
            card.setLatitude(meetup.getDisplayLatitude());
            card.setLongitude(meetup.getDisplayLongitude());
            card.setRadiusM(meetup.getLocationMode() == MeetupLocationMode.AREA
                    ? meetup.getRadiusM() : HIDDEN_EXACT_RADIUS_M);
        }
        card.setAreaLabel(meetup.getAreaLabel());
        card.setMeetupDate(meetup.getMeetupDate());
        card.setTimeMode(meetup.getTimeMode());
        card.setStartsAt(meetup.getStartsAt());
        card.setVisibleUntil(meetup.getVisibleUntil());
        card.setTimezone(MeetupSchedule.ZONE.getId());
        card.setJoinPolicy(meetup.getJoinPolicy());
        card.setMinAge(meetup.getMinAge());
        card.setMaxAge(meetup.getMaxAge());
        card.setMaxParticipants(meetup.getMaxParticipants());
        card.setJoinedCount(joined);
        card.setParticipantCount(joined + 1);
        card.setStatus(meetup.getStatus());
        card.setEffectiveStatus(MeetupEffectiveStatus.of(meetup.getStatus(), meetup.isPast(nowUtc)));
        if (isHost) {
            card.setViewerRole(MeetupViewerRole.HOST);
        } else if (viewerRow != null) {
            card.setViewerRole(MeetupViewerRole.PARTICIPANT);
            card.setViewerStatus(viewerRow.getStatus());
        } else {
            card.setViewerRole(MeetupViewerRole.NONE);
        }
        card.setCreatedAt(meetup.getCreatedAt());
    }

    private static boolean isJoined(MeetupParticipant viewerRow) {
        return viewerRow != null && viewerRow.getStatus() == MeetupParticipantStatus.JOINED;
    }

    private static Double distance(BigDecimal originLat, BigDecimal originLng, Meetup meetup) {
        if (originLat == null || originLng == null || Objects.isNull(meetup.getDisplayLatitude())) {
            return null;
        }
        double km = GeoDistance.kilometres(originLat.doubleValue(), originLng.doubleValue(),
                meetup.getDisplayLatitude().doubleValue(), meetup.getDisplayLongitude().doubleValue());
        return BigDecimal.valueOf(km).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
