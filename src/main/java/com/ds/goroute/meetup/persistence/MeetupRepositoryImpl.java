package com.ds.goroute.meetup.persistence;

import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupCount;
import com.ds.goroute.meetup.domain.MeetupInviteCandidate;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.domain.MeetupReportReason;
import com.ds.goroute.meetup.domain.MeetupStatusCount;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupRejectReason;
import com.ds.goroute.type.MeetupStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class MeetupRepositoryImpl implements MeetupRepository {

    private final MeetupMapper mapper;

    @Override
    public void insert(Meetup meetup) {
        mapper.insert(meetup);
    }

    @Override
    public Optional<Meetup> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    @Override
    public boolean lock(UUID id) {
        return mapper.lockById(id) != null;
    }

    @Override
    public boolean updateContent(Meetup meetup, long expectedVersion) {
        return mapper.updateContent(meetup, expectedVersion) == 1;
    }

    @Override
    public boolean review(UUID id, long expectedVersion, MeetupStatus status, MeetupRejectReason rejectReason,
                          String reviewNote, UUID reviewedBy, LocalDateTime now) {
        return mapper.updateReview(id, expectedVersion, status.name(),
                rejectReason == null ? null : rejectReason.name(), reviewNote, reviewedBy, now) == 1;
    }

    @Override
    public boolean withdraw(UUID id, UUID hostUserId, LocalDateTime now) {
        return mapper.updateWithdrawn(id, hostUserId, now) == 1;
    }

    @Override
    public boolean cancel(UUID id, UUID hostUserId, String reason, LocalDateTime now) {
        return mapper.updateCancelled(id, hostUserId, reason, now) == 1;
    }

    @Override
    public boolean remove(UUID id, String reason, UUID reviewedBy, LocalDateTime now) {
        return mapper.updateRemoved(id, reason, reviewedBy, now) == 1;
    }

    @Override
    public long countDiscoverable(MeetupDiscoveryQuery query) {
        return mapper.countDiscoverable(query);
    }

    @Override
    public List<Meetup> findDiscoverable(MeetupDiscoveryQuery query) {
        return mapper.findDiscoverable(query);
    }

    @Override
    public long countByHost(UUID hostUserId) {
        return mapper.countByHost(hostUserId);
    }

    @Override
    public List<Meetup> findByHost(UUID hostUserId, int limit, int offset) {
        return mapper.findByHost(hostUserId, limit, offset);
    }

    @Override
    public long countParticipations(UUID userId) {
        return mapper.countParticipations(userId);
    }

    @Override
    public List<Meetup> findParticipations(UUID userId, int limit, int offset) {
        return mapper.findParticipations(userId, limit, offset);
    }

    @Override
    public long countAdmin(MeetupAdminQuery query) {
        return mapper.countAdmin(query);
    }

    @Override
    public List<Meetup> findAdmin(MeetupAdminQuery query) {
        return mapper.findAdmin(query);
    }

    @Override
    public long countActiveByHost(UUID hostUserId, LocalDateTime now, UUID excludeId) {
        return mapper.countActiveByHost(hostUserId, now, excludeId);
    }

    @Override
    public long countSubmittedSince(UUID hostUserId, LocalDateTime since) {
        return mapper.countSubmittedSince(hostUserId, since);
    }

    @Override
    public Map<String, Long> countHostStatuses(UUID hostUserId) {
        Map<String, Long> counts = new HashMap<>();
        for (MeetupStatusCount row : mapper.countHostStatuses(hostUserId)) {
            counts.put(row.getStatus(), row.getTotal());
        }
        return counts;
    }

    @Override
    public Optional<Integer> findBirthYear(UUID userId) {
        return Optional.ofNullable(mapper.findBirthYear(userId));
    }

    @Override
    public Optional<MeetupParticipant> findParticipant(UUID meetupId, UUID userId) {
        return Optional.ofNullable(mapper.findParticipant(meetupId, userId));
    }

    @Override
    public Map<UUID, MeetupParticipant> findParticipantsForUser(UUID userId, Collection<UUID> meetupIds) {
        Map<UUID, MeetupParticipant> rows = new HashMap<>();
        if (userId == null || meetupIds == null || meetupIds.isEmpty()) {
            return rows;
        }
        for (MeetupParticipant row : mapper.findParticipantsForUser(userId, meetupIds)) {
            rows.put(row.getMeetupId(), row);
        }
        return rows;
    }

    @Override
    public List<MeetupParticipant> findParticipants(UUID meetupId, Collection<MeetupParticipantStatus> statuses,
                                                    int limit) {
        List<String> names = statuses == null ? List.of() : statuses.stream().map(Enum::name).toList();
        return mapper.findParticipants(meetupId, names, limit);
    }

    @Override
    public Map<UUID, Long> countJoined(Collection<UUID> meetupIds) {
        return toMap(meetupIds == null || meetupIds.isEmpty() ? List.of() : mapper.countJoined(meetupIds));
    }

    @Override
    public long countParticipantsWithStatus(UUID meetupId, MeetupParticipantStatus status) {
        return mapper.countParticipantsWithStatus(meetupId, status.name());
    }

    @Override
    public long countOpenRequests(UUID userId, LocalDateTime now) {
        return mapper.countOpenRequests(userId, now);
    }

    @Override
    public void upsertParticipant(MeetupParticipant participant) {
        mapper.upsertParticipant(participant);
    }

    @Override
    public boolean updateParticipantStatus(UUID meetupId, UUID userId, MeetupParticipantStatus from,
                                           MeetupParticipantStatus to, LocalDateTime now) {
        return mapper.updateParticipantStatus(meetupId, userId, from.name(), to.name(), now) == 1;
    }

    @Override
    public List<MeetupInviteCandidate> findInviteCandidates(UUID hostUserId, UUID meetupId, String query, int limit) {
        return mapper.findInviteCandidates(hostUserId, meetupId, query, limit);
    }

    @Override
    public boolean isInvitable(UUID hostUserId, UUID userId) {
        return hostUserId != null && userId != null && mapper.isInvitable(hostUserId, userId);
    }

    @Override
    public Map<UUID, Long> countReports(Collection<UUID> meetupIds) {
        return toMap(meetupIds == null || meetupIds.isEmpty() ? List.of() : mapper.countReports(meetupIds));
    }

    @Override
    public List<MeetupReportReason> findLatestReports(UUID meetupId, int limit) {
        return mapper.findLatestReports(meetupId, limit);
    }

    private static Map<UUID, Long> toMap(List<MeetupCount> rows) {
        Map<UUID, Long> counts = new HashMap<>();
        for (MeetupCount row : rows) {
            counts.put(row.getId(), row.getTotal());
        }
        return counts;
    }
}
