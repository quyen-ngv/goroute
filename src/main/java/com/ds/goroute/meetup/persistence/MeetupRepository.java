package com.ds.goroute.meetup.persistence;

import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupInviteCandidate;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.domain.MeetupReportReason;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupRejectReason;
import com.ds.goroute.type.MeetupStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence facade over {@link MeetupMapper}. Status-changing methods answer whether exactly
 * one row changed; a {@code false} means the guard (status, owner, window or version) did not
 * hold and the caller decides which conflict to report.
 */
public interface MeetupRepository {

    void insert(Meetup meetup);

    Optional<Meetup> findById(UUID id);

    /** Row lock for the rest of the transaction; empty when the meetup does not exist. */
    boolean lock(UUID id);

    boolean updateContent(Meetup meetup, long expectedVersion);

    boolean review(UUID id, long expectedVersion, MeetupStatus status, MeetupRejectReason rejectReason,
                   String reviewNote, UUID reviewedBy, LocalDateTime now);

    boolean withdraw(UUID id, UUID hostUserId, LocalDateTime now);

    boolean cancel(UUID id, UUID hostUserId, String reason, LocalDateTime now);

    boolean remove(UUID id, String reason, UUID reviewedBy, LocalDateTime now);

    long countDiscoverable(MeetupDiscoveryQuery query);

    List<Meetup> findDiscoverable(MeetupDiscoveryQuery query);

    long countByHost(UUID hostUserId);

    List<Meetup> findByHost(UUID hostUserId, int limit, int offset);

    long countParticipations(UUID userId);

    List<Meetup> findParticipations(UUID userId, int limit, int offset);

    long countAdmin(MeetupAdminQuery query);

    List<Meetup> findAdmin(MeetupAdminQuery query);

    long countActiveByHost(UUID hostUserId, LocalDateTime now, UUID excludeId);

    long countSubmittedSince(UUID hostUserId, LocalDateTime since);

    /** Stored status name → how many of the host's meetups are in it. */
    Map<String, Long> countHostStatuses(UUID hostUserId);

    Optional<Integer> findBirthYear(UUID userId);

    Optional<MeetupParticipant> findParticipant(UUID meetupId, UUID userId);

    /** The viewer's own row per meetup, for a page of meetups. */
    Map<UUID, MeetupParticipant> findParticipantsForUser(UUID userId, Collection<UUID> meetupIds);

    /** Rows of one meetup, oldest request first; all statuses when {@code statuses} is empty. */
    List<MeetupParticipant> findParticipants(UUID meetupId, Collection<MeetupParticipantStatus> statuses, int limit);

    /** JOINED seats per meetup; meetups with none are absent. */
    Map<UUID, Long> countJoined(Collection<UUID> meetupIds);

    long countParticipantsWithStatus(UUID meetupId, MeetupParticipantStatus status);

    long countOpenRequests(UUID userId, LocalDateTime now);

    void upsertParticipant(MeetupParticipant participant);

    boolean updateParticipantStatus(UUID meetupId, UUID userId, MeetupParticipantStatus from,
                                    MeetupParticipantStatus to, LocalDateTime now);

    /**
     * People the host follows or who follow the host, not deleted, with no block either way,
     * optionally filtered by name; each with their row in this meetup when they have one.
     */
    List<MeetupInviteCandidate> findInviteCandidates(UUID hostUserId, UUID meetupId, String query, int limit);

    /** Whether the host may invite this person: an existing user, a follow either way, no block. */
    boolean isInvitable(UUID hostUserId, UUID userId);

    /** User reports per meetup; meetups with none are absent. */
    Map<UUID, Long> countReports(Collection<UUID> meetupIds);

    List<MeetupReportReason> findLatestReports(UUID meetupId, int limit);
}
