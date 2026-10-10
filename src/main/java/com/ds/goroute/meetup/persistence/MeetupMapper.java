package com.ds.goroute.meetup.persistence;

import com.ds.goroute.meetup.domain.Meetup;
import com.ds.goroute.meetup.domain.MeetupCount;
import com.ds.goroute.meetup.domain.MeetupInviteCandidate;
import com.ds.goroute.meetup.domain.MeetupParticipant;
import com.ds.goroute.meetup.domain.MeetupReportReason;
import com.ds.goroute.meetup.domain.MeetupStatusCount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * SQL for meetups and their participants. Reads are single-table where they can be; people
 * (hosts, participants) are batch-loaded by the service, never joined per row. Every status
 * change is a guarded UPDATE: the expected status (and, where the API carries it, the expected
 * version) is in the WHERE clause and the service checks that exactly one row changed.
 */
@Mapper
public interface MeetupMapper {

    // --- meetups ----------------------------------------------------------------------

    void insert(@Param("m") Meetup meetup);

    Meetup findById(@Param("id") UUID id);

    /** Serialises joins and accepts on one meetup so the seat count cannot be overrun. */
    UUID lockById(@Param("id") UUID id);

    int updateContent(@Param("m") Meetup meetup, @Param("expectedVersion") long expectedVersion);

    int updateReview(@Param("id") UUID id,
                     @Param("expectedVersion") long expectedVersion,
                     @Param("status") String status,
                     @Param("rejectReason") String rejectReason,
                     @Param("reviewNote") String reviewNote,
                     @Param("reviewedBy") UUID reviewedBy,
                     @Param("now") LocalDateTime now);

    int updateWithdrawn(@Param("id") UUID id,
                        @Param("hostUserId") UUID hostUserId,
                        @Param("now") LocalDateTime now);

    int updateCancelled(@Param("id") UUID id,
                        @Param("hostUserId") UUID hostUserId,
                        @Param("reason") String reason,
                        @Param("now") LocalDateTime now);

    int updateRemoved(@Param("id") UUID id,
                      @Param("reason") String reason,
                      @Param("reviewedBy") UUID reviewedBy,
                      @Param("now") LocalDateTime now);

    long countDiscoverable(@Param("p") MeetupDiscoveryQuery query);

    List<Meetup> findDiscoverable(@Param("p") MeetupDiscoveryQuery query);

    long countByHost(@Param("hostUserId") UUID hostUserId);

    List<Meetup> findByHost(@Param("hostUserId") UUID hostUserId,
                            @Param("limit") int limit,
                            @Param("offset") int offset);

    long countParticipations(@Param("userId") UUID userId);

    List<Meetup> findParticipations(@Param("userId") UUID userId,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    long countAdmin(@Param("p") MeetupAdminQuery query);

    List<Meetup> findAdmin(@Param("p") MeetupAdminQuery query);

    long countActiveByHost(@Param("hostUserId") UUID hostUserId,
                           @Param("now") LocalDateTime now,
                           @Param("excludeId") UUID excludeId);

    long countSubmittedSince(@Param("hostUserId") UUID hostUserId,
                             @Param("since") LocalDateTime since);

    List<MeetupStatusCount> countHostStatuses(@Param("hostUserId") UUID hostUserId);

    Integer findBirthYear(@Param("userId") UUID userId);

    // --- participants -----------------------------------------------------------------

    MeetupParticipant findParticipant(@Param("meetupId") UUID meetupId, @Param("userId") UUID userId);

    List<MeetupParticipant> findParticipantsForUser(@Param("userId") UUID userId,
                                                    @Param("meetupIds") Collection<UUID> meetupIds);

    List<MeetupParticipant> findParticipants(@Param("meetupId") UUID meetupId,
                                             @Param("statuses") Collection<String> statuses,
                                             @Param("limit") int limit);

    List<MeetupCount> countJoined(@Param("meetupIds") Collection<UUID> meetupIds);

    long countParticipantsWithStatus(@Param("meetupId") UUID meetupId, @Param("status") String status);

    long countOpenRequests(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    void upsertParticipant(@Param("p") MeetupParticipant participant);

    int updateParticipantStatus(@Param("meetupId") UUID meetupId,
                                @Param("userId") UUID userId,
                                @Param("fromStatus") String fromStatus,
                                @Param("toStatus") String toStatus,
                                @Param("now") LocalDateTime now);

    // --- invitations ------------------------------------------------------------------

    List<MeetupInviteCandidate> findInviteCandidates(@Param("hostUserId") UUID hostUserId,
                                                     @Param("meetupId") UUID meetupId,
                                                     @Param("query") String query,
                                                     @Param("limit") int limit);

    boolean isInvitable(@Param("hostUserId") UUID hostUserId, @Param("userId") UUID userId);

    // --- reports ----------------------------------------------------------------------

    List<MeetupCount> countReports(@Param("meetupIds") Collection<UUID> meetupIds);

    List<MeetupReportReason> findLatestReports(@Param("meetupId") UUID meetupId, @Param("limit") int limit);
}
