package com.ds.goroute.meetup.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.dto.AdminMeetupDetail;
import com.ds.goroute.meetup.dto.AdminMeetupHost;
import com.ds.goroute.meetup.dto.AdminMeetupRow;
import com.ds.goroute.meetup.dto.MeetupRejectRequest;
import com.ds.goroute.meetup.persistence.MeetupAdminQuery;
import com.ds.goroute.meetup.persistence.MeetupRepository;
import com.ds.goroute.type.MeetupAdminSort;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupRejectReason;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MeetupAdminServiceImpl implements MeetupAdminService {

    static final int RECENT_LIMIT = 5;
    static final int REPORT_LIMIT = 5;

    private final MeetupRepository repository;
    private final MeetupResponseAssembler assembler;
    private final MeetupNotifier notifier;
    private final MeetupChatService meetupChat;

    /** Replaced in tests. */
    Clock clock = Clock.systemUTC();

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminMeetupRow> list(List<MeetupStatus> statuses, List<MeetupCategory> categories,
                                             String search, String provinceCode, LocalDate dateFrom,
                                             LocalDate dateTo, boolean hidePast, MeetupAdminSort sort,
                                             int page, int size) {
        LocalDateTime nowUtc = nowUtc();
        List<MeetupStatus> statusFilter = statuses == null ? List.of() : statuses;
        MeetupAdminSort effectiveSort = sort != null ? sort
                : statusFilter.equals(List.of(MeetupStatus.PENDING_REVIEW))
                ? MeetupAdminSort.DEADLINE : MeetupAdminSort.SUBMITTED_AT;
        MeetupAdminQuery query = MeetupAdminQuery.builder()
                .statuses(statusFilter.stream().map(Enum::name).distinct().toList())
                .categories(categories == null ? List.of() : categories.stream().map(Enum::name).distinct().toList())
                .search(search == null || search.isBlank() ? null : search.trim())
                .provinceCode(provinceCode == null || provinceCode.isBlank() ? null : provinceCode.trim())
                .dateFrom(dateFrom)
                .dateTo(dateTo)
                .hidePast(hidePast)
                .now(nowUtc)
                .sort(effectiveSort.name())
                .limit(size)
                .offset(page * size)
                .build();
        long total = repository.countAdmin(query);
        List<Meetup> rows = total == 0 ? List.of() : repository.findAdmin(query);
        if (rows.isEmpty()) {
            return PageResponse.of(List.of(), total, page, size);
        }
        List<UUID> ids = rows.stream().map(Meetup::getId).toList();
        Map<UUID, Long> joined = repository.countJoined(ids);
        Map<UUID, Long> reports = repository.countReports(ids);
        Map<UUID, User> hosts = assembler.people(rows.stream().map(Meetup::getHostUserId).toList());
        List<AdminMeetupRow> items = rows.stream().map(meetup -> AdminMeetupRow.builder()
                .id(meetup.getId())
                .title(meetup.getTitle())
                .category(meetup.getCategory())
                .hostId(meetup.getHostUserId())
                .hostName(MeetupResponseAssembler.displayName(hosts.get(meetup.getHostUserId())))
                .areaLabel(meetup.getAreaLabel())
                .provinceCode(meetup.getProvinceCode())
                .locationMode(meetup.getLocationMode())
                .meetupDate(meetup.getMeetupDate())
                .timeMode(meetup.getTimeMode())
                .startsAt(meetup.getStartsAt())
                .visibleUntil(meetup.getVisibleUntil())
                .status(meetup.getStatus())
                .effectiveStatus(MeetupEffectiveStatus.of(meetup.getStatus(), meetup.isPast(nowUtc)))
                .joinPolicy(meetup.getJoinPolicy())
                .joinedCount(joined.getOrDefault(meetup.getId(), 0L))
                .revision(meetup.getRevision())
                .resubmitCount(meetup.getResubmitCount())
                .submittedAt(meetup.getSubmittedAt())
                .reportCount(reports.getOrDefault(meetup.getId(), 0L))
                .dataVersion(meetup.getDataVersion())
                .build()).toList();
        return PageResponse.of(items, total, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminMeetupDetail detail(UUID meetupId) {
        return detailOf(load(meetupId));
    }

    @Override
    @Transactional
    public AdminMeetupDetail approve(UUID adminUserId, UUID meetupId, long expectedVersion) {
        Meetup meetup = requireReviewable(load(meetupId), MeetupStatus.APPROVED);
        if (!repository.review(meetupId, expectedVersion, MeetupStatus.APPROVED, null, null, adminUserId, nowUtc())) {
            throw versionConflict();
        }
        notifier.send(meetup.getHostUserId(), NotificationType.MEETUP_APPROVED, meetup, hostName(meetup), null,
                null, adminUserId);
        return detailOf(load(meetupId));
    }

    @Override
    @Transactional
    public AdminMeetupDetail reject(UUID adminUserId, UUID meetupId, MeetupRejectRequest request) {
        MeetupRejectReason reason = request.getReasonCode();
        String note = request.getNote() == null || request.getNote().isBlank() ? null : request.getNote().trim();
        if (reason == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "reasonCode is required");
        }
        if (reason == MeetupRejectReason.OTHER && note == null) {
            throw new BusinessException(ErrorConstant.MEETUP_REJECT_NOTE_REQUIRED, "A note is required for OTHER");
        }
        if (request.getExpectedVersion() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "expectedVersion is required");
        }
        Meetup meetup = requireReviewable(load(meetupId), MeetupStatus.REJECTED);
        if (!repository.review(meetupId, request.getExpectedVersion(), MeetupStatus.REJECTED, reason, note,
                adminUserId, nowUtc())) {
            throw versionConflict();
        }
        notifier.send(meetup.getHostUserId(), NotificationType.MEETUP_REJECTED, meetup, hostName(meetup), null,
                language -> note == null ? reason.label(language) : reason.label(language) + ": " + note,
                adminUserId);
        return detailOf(load(meetupId));
    }

    @Override
    @Transactional
    public AdminMeetupDetail remove(UUID adminUserId, UUID meetupId, String reason) {
        String cleanReason = reason == null || reason.isBlank() ? null : reason.trim();
        if (cleanReason == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "A reason is required");
        }
        Meetup meetup = load(meetupId);
        if (!meetup.getStatus().canTransitionTo(MeetupStatus.REMOVED, MeetupStatus.Actor.ADMIN)) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE, "Only an approved meetup can be removed");
        }
        if (!repository.remove(meetupId, cleanReason, adminUserId, nowUtc())) {
            throw versionConflict();
        }
        meetupChat.onMeetupClosed(meetupId);
        String hostName = hostName(meetup);
        notifier.send(meetup.getHostUserId(), NotificationType.MEETUP_REMOVED, meetup, hostName, null,
                language -> cleanReason, adminUserId);
        List<UUID> participants = repository.findParticipants(meetupId,
                        List.of(MeetupParticipantStatus.JOINED, MeetupParticipantStatus.REQUESTED,
                                MeetupParticipantStatus.INVITED),
                        MeetupServiceImpl.PARTICIPANT_LIST_LIMIT)
                .stream().map(MeetupParticipant::getUserId).toList();
        // Participants are told it is cancelled, not why: the removal reason is between the
        // operator and the host.
        notifier.sendAll(participants, NotificationType.MEETUP_CANCELLED, meetup, hostName, null, adminUserId);
        return detailOf(load(meetupId));
    }

    // --- helpers ------------------------------------------------------------------------

    /** PENDING_REVIEW and not past, or the matching conflict. */
    private Meetup requireReviewable(Meetup meetup, MeetupStatus target) {
        if (!meetup.getStatus().canTransitionTo(target, MeetupStatus.Actor.ADMIN)) {
            throw new BusinessException(ErrorConstant.MEETUP_INVALID_STATE, "Only a pending meetup can be reviewed");
        }
        if (meetup.isPast(nowUtc())) {
            throw new BusinessException(ErrorConstant.MEETUP_EXPIRED, "The meetup's window has passed");
        }
        return meetup;
    }

    private AdminMeetupDetail detailOf(Meetup meetup) {
        LocalDateTime nowUtc = nowUtc();
        UUID meetupId = meetup.getId();
        List<MeetupParticipant> rows = repository.findParticipants(meetupId, List.of(),
                MeetupServiceImpl.PARTICIPANT_LIST_LIMIT);
        long joined = rows.stream().filter(row -> row.getStatus() == MeetupParticipantStatus.JOINED).count();
        long requested = repository.countParticipantsWithStatus(meetupId, MeetupParticipantStatus.REQUESTED);
        Map<UUID, Long> reportCounts = repository.countReports(List.of(meetupId));
        List<AdminMeetupDetail.Report> latest = repository.findLatestReports(meetupId, REPORT_LIMIT).stream()
                .map(report -> AdminMeetupDetail.Report.builder()
                        .reason(report.getReason())
                        .note(report.getNote())
                        .createdAt(report.getCreatedAt())
                        .build())
                .toList();
        return AdminMeetupDetail.builder()
                .id(meetupId)
                .title(meetup.getTitle())
                .description(meetup.getDescription())
                .category(meetup.getCategory())
                .locationMode(meetup.getLocationMode())
                .latitude(meetup.getLatitude())
                .longitude(meetup.getLongitude())
                .displayLatitude(meetup.getDisplayLatitude())
                .displayLongitude(meetup.getDisplayLongitude())
                .radiusM(meetup.getRadiusM())
                .placeId(meetup.getPlaceId())
                .placeName(meetup.getPlaceName())
                .address(meetup.getAddress())
                .areaLabel(meetup.getAreaLabel())
                .wardCode(meetup.getWardCode())
                .provinceCode(meetup.getProvinceCode())
                .meetupDate(meetup.getMeetupDate())
                .timeMode(meetup.getTimeMode())
                .startsAt(meetup.getStartsAt())
                .visibleUntil(meetup.getVisibleUntil())
                .timezone(MeetupSchedule.ZONE.getId())
                .minAge(meetup.getMinAge())
                .maxAge(meetup.getMaxAge())
                .joinPolicy(meetup.getJoinPolicy())
                .maxParticipants(meetup.getMaxParticipants())
                .status(meetup.getStatus())
                .effectiveStatus(MeetupEffectiveStatus.of(meetup.getStatus(), meetup.isPast(nowUtc)))
                .rejectReason(meetup.getRejectReason())
                .reviewNote(meetup.getReviewNote())
                .reviewedBy(meetup.getReviewedBy())
                .reviewedAt(meetup.getReviewedAt())
                .submittedAt(meetup.getSubmittedAt())
                .resubmitCount(meetup.getResubmitCount())
                .revision(meetup.getRevision())
                .cancelReason(meetup.getCancelReason())
                .cancelledAt(meetup.getCancelledAt())
                .joinedCount(joined)
                .pendingRequestCount(requested)
                .host(host(meetup.getHostUserId()))
                .participants(assembler.participantResponses(rows, true))
                .reports(AdminMeetupDetail.Reports.builder()
                        .count(reportCounts.getOrDefault(meetupId, 0L))
                        .latest(latest)
                        .build())
                .dataVersion(meetup.getDataVersion())
                .createdAt(meetup.getCreatedAt())
                .updatedAt(meetup.getUpdatedAt())
                .build();
    }

    private AdminMeetupHost host(UUID hostUserId) {
        User user = assembler.people(List.of(hostUserId)).get(hostUserId);
        Map<String, Long> counts = repository.countHostStatuses(hostUserId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        List<AdminMeetupHost.Recent> recent = repository.findByHost(hostUserId, RECENT_LIMIT, 0).stream()
                .map(row -> AdminMeetupHost.Recent.builder()
                        .id(row.getId())
                        .title(row.getTitle())
                        .status(row.getStatus().name())
                        .meetupDate(row.getMeetupDate())
                        .build())
                .toList();
        return AdminMeetupHost.builder()
                .id(hostUserId)
                .name(MeetupResponseAssembler.displayName(user))
                .username(user == null ? null : user.getUsername())
                .avatarUrl(user == null ? null : user.getAvatarUrl())
                .createdAt(user == null ? null : user.getCreatedAt())
                .birthYear(repository.findBirthYear(hostUserId).orElse(null))
                .hostedTotal(total)
                .approvedTotal(counts.getOrDefault(MeetupStatus.APPROVED.name(), 0L))
                .rejectedTotal(counts.getOrDefault(MeetupStatus.REJECTED.name(), 0L))
                .removedTotal(counts.getOrDefault(MeetupStatus.REMOVED.name(), 0L))
                .cancelledTotal(counts.getOrDefault(MeetupStatus.CANCELLED.name(), 0L))
                .recent(recent)
                .build();
    }

    private String hostName(Meetup meetup) {
        return MeetupResponseAssembler.displayName(
                assembler.people(List.of(meetup.getHostUserId())).get(meetup.getHostUserId()));
    }

    private Meetup load(UUID meetupId) {
        return repository.findById(meetupId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.MEETUP_NOT_FOUND, "Meetup not found"));
    }

    private LocalDateTime nowUtc() {
        return MeetupSchedule.nowUtc(clock.instant());
    }

    private static BusinessException versionConflict() {
        return new BusinessException(ErrorConstant.ALREADY_PROCESSED, "The meetup changed; reload and retry");
    }
}
