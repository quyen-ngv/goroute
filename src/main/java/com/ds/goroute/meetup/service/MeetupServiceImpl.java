package com.ds.goroute.meetup.service;

import com.ds.goroute.utils.GeoBoundingBox;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.entity.Ward;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.dto.MeetupContextResponse;
import com.ds.goroute.meetup.dto.MeetupDetailResponse;
import com.ds.goroute.meetup.dto.MeetupJoinResponse;
import com.ds.goroute.meetup.dto.MeetupParticipantResponse;
import com.ds.goroute.meetup.dto.MeetupSummaryResponse;
import com.ds.goroute.meetup.dto.MeetupUpsertRequest;
import com.ds.goroute.meetup.persistence.MeetupDiscoveryQuery;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.ContentModerationService;
import com.ds.goroute.service.GeoService;
import com.ds.goroute.service.UserBlockService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.MeetupCannotHostReason;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupViewerRole;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetupServiceImpl implements MeetupService {

    /** The only AREA radii the create flow offers. */
    public static final List<Integer> AREA_RADII_M = List.of(300, 1000, 3000);
    static final int MAX_AGE = 99;
    static final int MAX_PARTICIPANTS = 50;
    /** Bounds the participant list; seats are capped at 50, requests by the per-user cap. */
    static final int PARTICIPANT_LIST_LIMIT = 200;

    private final MeetupRepository repository;
    private final MeetupAccess access;
    private final BusinessConfigService config;
    private final GeoService geoService;
    private final UserBlockService userBlocks;
    private final ContentModerationService moderation;
    private final MeetupNotifier notifier;
    private final MeetupResponseAssembler assembler;
    private final MeetupChatService meetupChat;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    // --- reads ------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public MeetupContextResponse context(UUID userId) {
        Instant now = clock.instant();
        boolean enabled = access.isEnabled(userId);
        Integer birthYear = repository.findBirthYear(userId).orElse(null);
        MeetupCannotHostReason reason = null;
        if (enabled) {
            if (birthYear == null) {
                reason = MeetupCannotHostReason.BIRTH_YEAR_REQUIRED;
            } else if (MeetupSchedule.age(birthYear, now) < MeetupAccess.MIN_AGE) {
                reason = MeetupCannotHostReason.UNDERAGE;
            } else if (repository.countActiveByHost(userId, MeetupSchedule.nowUtc(now), null)
                    >= config.getInt(BusinessConfigKey.MEETUP_MAX_ACTIVE_PER_HOST)) {
                reason = MeetupCannotHostReason.HOST_LIMIT;
            }
        }
        return MeetupContextResponse.builder()
                .enabled(enabled)
                .birthYear(birthYear)
                .canHost(enabled && reason == null)
                .cannotHostReason(reason)
                .maxDaysAhead(config.getInt(BusinessConfigKey.MEETUP_MAX_DAYS_AHEAD))
                .exactMinLeadMinutes(config.getInt(BusinessConfigKey.MEETUP_EXACT_MIN_LEAD_MINUTES))
                .exactVisibleHours(config.getInt(BusinessConfigKey.MEETUP_EXACT_VISIBLE_HOURS))
                .areaRadiiM(AREA_RADII_M)
                .categories(Arrays.asList(MeetupCategory.values()))
                .today(MeetupSchedule.today(now))
                .timezone(MeetupSchedule.ZONE.getId())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MeetupSummaryResponse> discover(UUID userId, BigDecimal latitude, BigDecimal longitude,
                                                        double radiusKm, List<MeetupCategory> categories,
                                                        int page, int size) {
        access.requireEnabled(userId);
        LocalDateTime now = MeetupSchedule.nowUtc(clock.instant());
        GeoBoundingBox box = GeoBoundingBox.around(latitude.doubleValue(), longitude.doubleValue(), radiusKm);
        MeetupDiscoveryQuery query = MeetupDiscoveryQuery.builder()
                .viewerId(userId)
                .latitude(latitude)
                .longitude(longitude)
                .radiusKm(radiusKm)
                .minLat(degrees(box.minLat(), RoundingMode.FLOOR))
                .maxLat(degrees(box.maxLat(), RoundingMode.CEILING))
                .minLng(degrees(box.minLng(), RoundingMode.FLOOR))
                .maxLng(degrees(box.maxLng(), RoundingMode.CEILING))
                .categories(categories == null ? List.of() : categories.stream().map(Enum::name).distinct().toList())
                .now(now)
                .limit(size)
                .offset(page * size)
                .build();
        long total = repository.countDiscoverable(query);
        List<Meetup> rows = total == 0 ? List.of() : repository.findDiscoverable(query);
        return PageResponse.of(assembler.summaries(rows, userId, latitude, longitude, now), total, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MeetupSummaryResponse> mine(UUID userId, MeetupViewerRole role, int page, int size) {
        access.requireEnabled(userId);
        LocalDateTime now = MeetupSchedule.nowUtc(clock.instant());
        boolean hosted = role != MeetupViewerRole.PARTICIPANT;
        long total = hosted ? repository.countByHost(userId) : repository.countParticipations(userId);
        List<Meetup> rows = total == 0 ? List.of()
                : hosted ? repository.findByHost(userId, size, page * size)
                : repository.findParticipations(userId, size, page * size);
        return PageResponse.of(assembler.summaries(rows, userId, null, null, now), total, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetupDetailResponse detail(UUID userId, UUID meetupId) {
        access.requireEnabled(userId);
        Meetup meetup = requireVisible(userId, meetupId);
        return detailFor(meetup, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MeetupParticipantResponse> participants(UUID userId, UUID meetupId) {
        access.requireEnabled(userId);
        Meetup meetup = requireVisible(userId, meetupId);
        MeetupParticipant viewerRow = repository.findParticipant(meetupId, userId).orElse(null);
        return assembler.participants(meetup, userId, viewerRow, PARTICIPANT_LIST_LIMIT);
    }

    // --- host: create, edit, withdraw, cancel -----------------------------------------

    @Override
    @Transactional
    public MeetupDetailResponse create(UUID userId, MeetupUpsertRequest request) {
        access.requireEnabled(userId);
        Instant now = clock.instant();
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(now);
        int hostAge = access.requireAdult(userId, now);
        UUID meetupId = UUID.randomUUID();
        Meetup meetup = validated(request, hostAge, now);
        requireHostCapacity(userId, nowUtc, null);
        requireSubmissionAllowance(userId, nowUtc);

        meetup.setId(meetupId);
        meetup.setHostUserId(userId);
        applyDisplayPoint(meetup);
        meetup.setStatus(MeetupStatus.initial());
        meetup.setSubmittedAt(nowUtc);
        meetup.setResubmitCount(0);
        meetup.setRevision(1);
        meetup.setDataVersion(0L);
        meetup.setCreatedAt(nowUtc);
        meetup.setUpdatedAt(nowUtc);
        repository.insert(meetup);
        meetupChat.onMeetupCreated(meetupId);
        return detailFor(meetup, userId);
    }

    @Override
    @Transactional
    public MeetupDetailResponse update(UUID userId, UUID meetupId, MeetupUpsertRequest request) {
        access.requireEnabled(userId);
        if (request.getExpectedVersion() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "expectedVersion is required");
        }
        Instant now = clock.instant();
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(now);
        int hostAge = access.requireAdult(userId, now);
        Meetup current = requireHosted(userId, meetupId);
        if (!current.getStatus().isHostEditable()) {
            throw new BusinessException(ErrorConstant.MEETUP_NOT_EDITABLE,
                    "Only a meetup waiting for review or rejected can be edited");
        }
        boolean resubmit = current.getStatus() == MeetupStatus.REJECTED;
        int resubmits = current.getResubmitCount() == null ? 0 : current.getResubmitCount();
        if (resubmit && resubmits >= config.getInt(BusinessConfigKey.MEETUP_MAX_RESUBMITS)) {
            throw new BusinessException(ErrorConstant.MEETUP_RESUBMIT_LIMIT, "No resubmits left");
        }
        Meetup meetup = validated(request, hostAge, now);
        if (resubmit) {
            requireHostCapacity(userId, nowUtc, meetupId);
            requireSubmissionAllowance(userId, nowUtc);
        }
        meetup.setId(meetupId);
        meetup.setHostUserId(userId);
        applyDisplayPoint(meetup);
        meetup.setStatus(MeetupStatus.PENDING_REVIEW);
        meetup.setSubmittedAt(resubmit ? nowUtc : current.getSubmittedAt());
        meetup.setResubmitCount(resubmit ? resubmits + 1 : resubmits);
        meetup.setRevision((current.getRevision() == null ? 1 : current.getRevision()) + 1);
        meetup.setUpdatedAt(nowUtc);
        if (!repository.updateContent(meetup, request.getExpectedVersion())) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The meetup changed; reload and retry");
        }
        return detailFor(reload(meetupId), userId);
    }

    @Override
    @Transactional
    public MeetupDetailResponse withdraw(UUID userId, UUID meetupId) {
        access.requireEnabled(userId);
        Meetup meetup = requireHosted(userId, meetupId);
        if (!meetup.getStatus().canTransitionTo(MeetupStatus.WITHDRAWN, MeetupStatus.Actor.HOST)) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE, "Only a pending meetup can be withdrawn");
        }
        if (!repository.withdraw(meetupId, userId, MeetupSchedule.nowUtc(clock.instant()))) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The meetup changed; reload and retry");
        }
        meetupChat.onMeetupClosed(meetupId);
        return detailFor(reload(meetupId), userId);
    }

    @Override
    @Transactional
    public MeetupDetailResponse cancel(UUID userId, UUID meetupId, String reason) {
        access.requireEnabled(userId);
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(clock.instant());
        Meetup meetup = requireHosted(userId, meetupId);
        if (!meetup.getStatus().canTransitionTo(MeetupStatus.CANCELLED, MeetupStatus.Actor.HOST)) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE, "Only an approved meetup can be cancelled");
        }
        if (meetup.isPast(nowUtc)) {
            throw new BusinessException(ErrorConstant.MEETUP_EXPIRED, "The meetup has already ended");
        }
        String cleanReason = blankToNull(reason);
        if (!repository.cancel(meetupId, userId, cleanReason, nowUtc)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The meetup changed; reload and retry");
        }
        meetupChat.onMeetupClosed(meetupId);
        notifyParticipantsCancelled(meetup, cleanReason, userId);
        return detailFor(reload(meetupId), userId);
    }

    // --- joining ------------------------------------------------------------------------

    @Override
    @Transactional
    public MeetupJoinResponse join(UUID userId, UUID meetupId, String message) {
        access.requireEnabled(userId);
        Instant now = clock.instant();
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(now);
        int age = access.requireAdult(userId, now);
        requireVisible(userId, meetupId);
        repository.lock(meetupId);
        Meetup meetup = reload(meetupId);
        if (meetup.getHostUserId().equals(userId)) {
            throw new BusinessException(ErrorConstant.MEETUP_JOIN_NOT_ALLOWED, "You host this meetup");
        }
        if (meetup.getStatus() != MeetupStatus.APPROVED) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE, "This meetup is not open");
        }
        if (meetup.isPast(nowUtc)) {
            throw new BusinessException(ErrorConstant.MEETUP_EXPIRED, "The meetup has already ended");
        }
        if (age < meetup.getMinAge() || (meetup.getMaxAge() != null && age > meetup.getMaxAge())) {
            throw new BusinessException(ErrorConstant.MEETUP_AGE_NOT_ALLOWED, "Your age is outside this meetup's range");
        }
        MeetupParticipant existing = repository.findParticipant(meetupId, userId).orElse(null);
        if (existing != null) {
            if (existing.getStatus().isActive()) {
                return new MeetupJoinResponse(existing.getStatus());
            }
            if (!existing.getStatus().canRejoin()) {
                throw new BusinessException(ErrorConstant.MEETUP_JOIN_NOT_ALLOWED, "You cannot join this meetup");
            }
        }
        requireSeat(meetup);
        boolean open = meetup.getJoinPolicy() == MeetupJoinPolicy.OPEN;
        if (!open && repository.countOpenRequests(userId, nowUtc)
                >= config.getInt(BusinessConfigKey.MEETUP_MAX_OPEN_REQUESTS_PER_USER)) {
            throw new BusinessException(ErrorConstant.MEETUP_REQUEST_LIMIT, "Too many pending join requests");
        }
        MeetupParticipantStatus status = open ? MeetupParticipantStatus.JOINED : MeetupParticipantStatus.REQUESTED;
        repository.upsertParticipant(MeetupParticipant.builder()
                .meetupId(meetupId)
                .userId(userId)
                .status(status)
                .message(open ? null : blankToNull(message))
                .requestedAt(nowUtc)
                .decidedAt(open ? nowUtc : null)
                .updatedAt(nowUtc)
                .build());
        if (open) {
            meetupChat.onMembershipChanged(meetupId);
        }

        Map<UUID, User> people = assembler.people(List.of(userId, meetup.getHostUserId()));
        notifier.send(meetup.getHostUserId(),
                open ? NotificationType.MEETUP_PARTICIPANT_JOINED : NotificationType.MEETUP_JOIN_REQUESTED,
                meetup,
                MeetupResponseAssembler.displayName(people.get(meetup.getHostUserId())),
                MeetupResponseAssembler.displayName(people.get(userId)),
                null, userId);
        return new MeetupJoinResponse(status);
    }

    @Override
    @Transactional
    public MeetupJoinResponse leave(UUID userId, UUID meetupId) {
        access.requireEnabled(userId);
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(clock.instant());
        reload(meetupId);
        MeetupParticipant row = repository.findParticipant(meetupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.MEETUP_PARTICIPANT_NOT_FOUND,
                        "You are not in this meetup"));
        MeetupParticipantStatus target = switch (row.getStatus()) {
            case JOINED -> MeetupParticipantStatus.LEFT;
            case REQUESTED -> MeetupParticipantStatus.WITHDRAWN;
            default -> throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE,
                    "You have already left this meetup");
        };
        if (!repository.updateParticipantStatus(meetupId, userId, row.getStatus(), target, nowUtc)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "Your status changed; reload and retry");
        }
        if (target == MeetupParticipantStatus.LEFT) {
            meetupChat.onMembershipChanged(meetupId);
        }
        return new MeetupJoinResponse(target);
    }

    // --- host decisions -----------------------------------------------------------------

    @Override
    @Transactional
    public MeetupParticipantResponse accept(UUID hostUserId, UUID meetupId, UUID participantUserId) {
        access.requireEnabled(hostUserId);
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(clock.instant());
        requireHosted(hostUserId, meetupId);
        repository.lock(meetupId);
        Meetup meetup = requireApproved(reload(meetupId));
        if (meetup.isPast(nowUtc)) {
            throw new BusinessException(ErrorConstant.MEETUP_EXPIRED, "The meetup has already ended");
        }
        MeetupParticipant row = requireParticipant(meetupId, participantUserId, MeetupParticipantStatus.REQUESTED);
        requireSeat(meetup);
        MeetupParticipant updated = transition(row, MeetupParticipantStatus.JOINED, nowUtc);
        meetupChat.onMembershipChanged(meetupId);
        notifyDecision(meetup, participantUserId, NotificationType.MEETUP_REQUEST_ACCEPTED, hostUserId);
        return hostView(updated);
    }

    @Override
    @Transactional
    public MeetupParticipantResponse decline(UUID hostUserId, UUID meetupId, UUID participantUserId) {
        access.requireEnabled(hostUserId);
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(clock.instant());
        Meetup meetup = requireApproved(requireHosted(hostUserId, meetupId));
        MeetupParticipant row = requireParticipant(meetupId, participantUserId, MeetupParticipantStatus.REQUESTED);
        MeetupParticipant updated = transition(row, MeetupParticipantStatus.DECLINED, nowUtc);
        notifyDecision(meetup, participantUserId, NotificationType.MEETUP_REQUEST_DECLINED, hostUserId);
        return hostView(updated);
    }

    @Override
    @Transactional
    public MeetupParticipantResponse removeParticipant(UUID hostUserId, UUID meetupId, UUID participantUserId) {
        access.requireEnabled(hostUserId);
        LocalDateTime nowUtc = MeetupSchedule.nowUtc(clock.instant());
        requireApproved(requireHosted(hostUserId, meetupId));
        MeetupParticipant row = requireParticipant(meetupId, participantUserId, MeetupParticipantStatus.JOINED);
        MeetupParticipant updated = transition(row, MeetupParticipantStatus.REMOVED, nowUtc);
        meetupChat.onMembershipChanged(meetupId);
        return hostView(updated);
    }

    // --- helpers ------------------------------------------------------------------------

    /**
     * A meetup the viewer may open. The host always may. Anyone else needs it APPROVED, or
     * CANCELLED while they still hold a request or a seat (so the cancellation notice opens),
     * not taken down, and no block in either direction with the host. Everything else is 404.
     */
    private Meetup requireVisible(UUID viewerId, UUID meetupId) {
        Meetup meetup = reload(meetupId);
        if (meetup.getHostUserId().equals(viewerId)) {
            return meetup;
        }
        boolean visible = switch (meetup.getStatus()) {
            case APPROVED -> true;
            case CANCELLED -> repository.findParticipant(meetupId, viewerId)
                    .map(row -> row.getStatus().isActive())
                    .orElse(false);
            default -> false;
        };
        if (!visible
                || moderation.isTakenDown(ModeratedContentType.MEETUP, meetupId)
                || userBlocks.blockedBetween(viewerId, meetup.getHostUserId())) {
            throw notFound();
        }
        return meetup;
    }

    /** The meetup when the caller hosts it; 404 otherwise, so a guessed id confirms nothing. */
    private Meetup requireHosted(UUID hostUserId, UUID meetupId) {
        Meetup meetup = reload(meetupId);
        if (!meetup.getHostUserId().equals(hostUserId)) {
            throw notFound();
        }
        return meetup;
    }

    private static Meetup requireApproved(Meetup meetup) {
        if (meetup.getStatus() != MeetupStatus.APPROVED) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE, "This meetup is not live");
        }
        return meetup;
    }

    private Meetup reload(UUID meetupId) {
        return repository.findById(meetupId).orElseThrow(MeetupServiceImpl::notFound);
    }

    private MeetupParticipant requireParticipant(UUID meetupId, UUID userId, MeetupParticipantStatus expected) {
        MeetupParticipant row = repository.findParticipant(meetupId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.MEETUP_PARTICIPANT_NOT_FOUND,
                        "This person is not in the meetup"));
        if (row.getStatus() != expected) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE,
                    "This person is " + row.getStatus() + ", not " + expected);
        }
        return row;
    }

    private MeetupParticipant transition(MeetupParticipant row, MeetupParticipantStatus target, LocalDateTime nowUtc) {
        if (!repository.updateParticipantStatus(row.getMeetupId(), row.getUserId(), row.getStatus(), target, nowUtc)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The request changed; reload and retry");
        }
        row.setStatus(target);
        row.setDecidedAt(nowUtc);
        row.setUpdatedAt(nowUtc);
        return row;
    }

    private MeetupParticipantResponse hostView(MeetupParticipant row) {
        User user = assembler.people(List.of(row.getUserId())).get(row.getUserId());
        return assembler.participant(row, user, true);
    }

    /** 409 MEETUP_FULL when every seat (host excluded) is taken. Call under the meetup lock. */
    private void requireSeat(Meetup meetup) {
        if (meetup.getMaxParticipants() == null) {
            return;
        }
        long joined = repository.countParticipantsWithStatus(meetup.getId(), MeetupParticipantStatus.JOINED);
        if (joined >= meetup.getMaxParticipants()) {
            throw new BusinessException(ErrorConstant.MEETUP_FULL, "This meetup is full");
        }
    }

    private void requireHostCapacity(UUID hostUserId, LocalDateTime nowUtc, UUID excludeId) {
        if (repository.countActiveByHost(hostUserId, nowUtc, excludeId)
                >= config.getInt(BusinessConfigKey.MEETUP_MAX_ACTIVE_PER_HOST)) {
            throw new BusinessException(ErrorConstant.MEETUP_HOST_LIMIT, "Too many active meetups");
        }
    }

    private void requireSubmissionAllowance(UUID hostUserId, LocalDateTime nowUtc) {
        if (repository.countSubmittedSince(hostUserId, nowUtc.minusHours(24))
                >= config.getInt(BusinessConfigKey.MEETUP_MAX_SUBMISSIONS_PER_DAY)) {
            throw new BusinessException(ErrorConstant.MEETUP_RATE_LIMITED, "Too many submissions in 24 hours");
        }
    }

    /**
     * Applies every content rule of SPEC_V2 §2.3 and returns the validated content (no id, host,
     * status or display point yet). Order: location, ages, time.
     */
    private Meetup validated(MeetupUpsertRequest request, int hostAge, Instant now) {
        String title = blankToNull(request.getTitle());
        if (title == null || request.getCategory() == null || request.getLocationMode() == null
                || request.getLatitude() == null || request.getLongitude() == null
                || request.getTimeMode() == null || request.getJoinPolicy() == null || request.getMinAge() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A required field is missing");
        }
        Integer radius = null;
        if (request.getLocationMode() == MeetupLocationMode.AREA) {
            radius = request.getRadiusM();
            if (radius == null || !AREA_RADII_M.contains(radius)) {
                throw new BusinessException(ErrorConstant.MEETUP_INVALID_LOCATION,
                        "An area must be 300, 1000 or 3000 metres");
            }
        }
        Ward ward = geoService.resolveWard(request.getLatitude(), request.getLongitude())
                .orElseThrow(() -> new BusinessException(ErrorConstant.MEETUP_OUTSIDE_SERVICE_AREA,
                        "The point is not inside Vietnam"));

        int minAge = request.getMinAge();
        Integer maxAge = request.getMaxAge();
        if (minAge < MeetupAccess.MIN_AGE || minAge > MAX_AGE
                || (maxAge != null && (maxAge < minAge || maxAge > MAX_AGE))) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_AGE_RANGE, "The age range is not valid");
        }
        if (hostAge < minAge || (maxAge != null && hostAge > maxAge)) {
            throw new BusinessException(ErrorConstant.MEETUP_AGE_RANGE_EXCLUDES_HOST,
                    "Your own age must be inside the range");
        }
        Integer maxParticipants = request.getMaxParticipants();
        if (maxParticipants != null && (maxParticipants < 1 || maxParticipants > MAX_PARTICIPANTS)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Max participants must be 1..50");
        }

        MeetupSchedule.Window window = MeetupSchedule.window(request.getMeetupDate(), request.getTimeMode(),
                request.getStartTime(), now,
                config.getInt(BusinessConfigKey.MEETUP_MAX_DAYS_AHEAD),
                config.getInt(BusinessConfigKey.MEETUP_EXACT_MIN_LEAD_MINUTES),
                config.getInt(BusinessConfigKey.MEETUP_EXACT_VISIBLE_HOURS));

        return Meetup.builder()
                .title(title)
                .description(blankToNull(request.getDescription()))
                .category(request.getCategory())
                .locationMode(request.getLocationMode())
                .latitude(request.getLatitude().setScale(7, RoundingMode.HALF_UP))
                .longitude(request.getLongitude().setScale(7, RoundingMode.HALF_UP))
                .radiusM(radius)
                .placeId(request.getPlaceId())
                .placeName(blankToNull(request.getPlaceName()))
                .address(blankToNull(request.getAddress()))
                .areaLabel(areaLabel(ward))
                .wardCode(ward.getCode())
                .provinceCode(ward.getProvinceCode())
                .meetupDate(request.getMeetupDate())
                .timeMode(request.getTimeMode())
                .startsAt(window.startsAt())
                .visibleUntil(window.visibleUntil())
                .minAge(minAge)
                .maxAge(maxAge)
                .joinPolicy(request.getJoinPolicy())
                .maxParticipants(maxParticipants)
                .build();
    }

    private static void applyDisplayPoint(Meetup meetup) {
        MeetupDisplayPoint.Point point = MeetupDisplayPoint.of(meetup.getId(), meetup.getLatitude(),
                meetup.getLongitude(), meetup.getLocationMode(), meetup.getRadiusM());
        meetup.setDisplayLatitude(point.latitude());
        meetup.setDisplayLongitude(point.longitude());
    }

    static String areaLabel(Ward ward) {
        String wardName = ward.getFullName() != null ? ward.getFullName() : ward.getName();
        if (wardName == null) {
            return ward.getProvinceName();
        }
        return ward.getProvinceName() == null ? wardName : wardName + ", " + ward.getProvinceName();
    }

    private MeetupDetailResponse detailFor(Meetup meetup, UUID viewerId) {
        MeetupParticipant viewerRow = meetup.getHostUserId().equals(viewerId) ? null
                : repository.findParticipant(meetup.getId(), viewerId).orElse(null);
        return assembler.detail(meetup, viewerId, viewerRow, MeetupSchedule.nowUtc(clock.instant()),
                config.getInt(BusinessConfigKey.MEETUP_MAX_RESUBMITS));
    }

    private void notifyParticipantsCancelled(Meetup meetup, String reason, UUID actorId) {
        List<UUID> recipients = repository.findParticipants(meetup.getId(),
                        List.of(MeetupParticipantStatus.JOINED, MeetupParticipantStatus.REQUESTED), PARTICIPANT_LIST_LIMIT)
                .stream().map(MeetupParticipant::getUserId).toList();
        if (recipients.isEmpty()) {
            return;
        }
        String hostName = MeetupResponseAssembler.displayName(
                assembler.people(List.of(meetup.getHostUserId())).get(meetup.getHostUserId()));
        notifier.sendAll(recipients, NotificationType.MEETUP_CANCELLED, meetup, hostName, language -> reason, actorId);
    }

    private void notifyDecision(Meetup meetup, UUID participantUserId, NotificationType type, UUID hostUserId) {
        Map<UUID, User> people = assembler.people(List.of(participantUserId, hostUserId));
        notifier.send(participantUserId, type, meetup,
                MeetupResponseAssembler.displayName(people.get(hostUserId)),
                MeetupResponseAssembler.displayName(people.get(participantUserId)),
                null, hostUserId);
    }

    /** Rounded outward (FLOOR for a minimum, CEILING for a maximum) so the box never shrinks. */
    private static BigDecimal degrees(double value, RoundingMode rounding) {
        return BigDecimal.valueOf(value).setScale(7, rounding);
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.MEETUP_NOT_FOUND, "Meetup not found");
    }
}
