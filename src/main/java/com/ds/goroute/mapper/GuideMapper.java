package com.ds.goroute.mapper;

import com.ds.goroute.entity.GuideAvailability;
import com.ds.goroute.entity.GuideBooking;
import com.ds.goroute.entity.GuidePayoutEntry;
import com.ds.goroute.entity.GuideProfile;
import com.ds.goroute.entity.GuideReview;
import com.ds.goroute.entity.GuideService;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** SQL for the guide marketplace (epic 07). One bounded context, one mapper. */
@Mapper
public interface GuideMapper {

    // --- profiles ---------------------------------------------------------------------

    int insertProfile(GuideProfile profile);

    int updateProfile(GuideProfile profile);

    int submitProfile(@Param("id") UUID id, @Param("userId") UUID userId);

    int decideProfile(@Param("id") UUID id,
                      @Param("status") String status,
                      @Param("decidedBy") UUID decidedBy,
                      @Param("decisionNote") String decisionNote,
                      @Param("informationRequested") String informationRequested);

    GuideProfile findProfileById(@Param("id") UUID id);

    GuideProfile findProfileByUser(@Param("userId") UUID userId);

    List<GuideProfile> findProfileQueue(@Param("status") String status,
                                        @Param("limit") int limit,
                                        @Param("offset") int offset);

    long countProfileQueue(@Param("status") String status);

    int refreshProfileStats(@Param("guideId") UUID guideId);

    int insertIdentityDocument(@Param("id") UUID id,
                               @Param("guideId") UUID guideId,
                               @Param("documentType") String documentType,
                               @Param("fileUrl") String fileUrl,
                               @Param("purgeAfter") LocalDateTime purgeAfter);

    List<Map<String, Object>> findIdentityDocuments(@Param("guideId") UUID guideId);

    /** Documents past their disposal date; kept as a query so the job cannot miss any. */
    List<Map<String, Object>> findDocumentsToPurge(@Param("limit") int limit);

    int markDocumentPurged(@Param("id") UUID id);

    // --- services ---------------------------------------------------------------------

    int insertService(GuideService service);

    int updateService(GuideService service);

    GuideService findServiceById(@Param("id") UUID id);

    List<GuideService> findServicesByGuide(@Param("guideId") UUID guideId);

    int countListedServices(@Param("guideId") UUID guideId);

    /**
     * Public search. Organic results only -- promoted placements are selected separately
     * and joined at the presentation layer so they can never lose their label.
     */
    List<GuideService> searchServices(@Param("provinceCode") String provinceCode,
                                      @Param("language") String language,
                                      @Param("maxPrice") java.math.BigDecimal maxPrice,
                                      @Param("date") LocalDate date,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);

    long countSearchServices(@Param("provinceCode") String provinceCode,
                             @Param("language") String language,
                             @Param("maxPrice") java.math.BigDecimal maxPrice,
                             @Param("date") LocalDate date);

    int pauseServicesOverLimit(@Param("guideId") UUID guideId, @Param("keep") int keep);

    // --- availability -----------------------------------------------------------------

    int upsertAvailability(GuideAvailability availability);

    List<GuideAvailability> findAvailability(@Param("guideId") UUID guideId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    GuideAvailability findAvailabilityForDate(@Param("guideId") UUID guideId,
                                              @Param("date") LocalDate date);

    // --- bookings ---------------------------------------------------------------------

    int insertBooking(GuideBooking booking);

    GuideBooking findBookingById(@Param("id") UUID id);

    GuideBooking findBookingByIdempotencyKey(@Param("travelerId") UUID travelerId,
                                             @Param("idempotencyKey") String idempotencyKey);

    /**
     * Guests already committed for a date, read with the rows locked.
     *
     * <p>Without the lock two people booking the last places at the same moment both read
     * the old count and both succeed, and the guide finds out on the day.
     */
    Integer sumCommittedGuestsForUpdate(@Param("guideId") UUID guideId, @Param("date") LocalDate date);

    int updateBookingStatus(@Param("id") UUID id,
                            @Param("status") String status,
                            @Param("expectedStatus") String expectedStatus,
                            @Param("paymentStatus") String paymentStatus,
                            @Param("reason") String reason);

    int freezePayout(@Param("id") UUID id, @Param("frozen") boolean frozen);

    int markPayoutReleased(@Param("id") UUID id);

    List<GuideBooking> findBookingsForGuide(@Param("guideId") UUID guideId,
                                            @Param("status") String status,
                                            @Param("limit") int limit,
                                            @Param("offset") int offset);

    long countBookingsForGuide(@Param("guideId") UUID guideId, @Param("status") String status);

    List<GuideBooking> findBookingsForTraveler(@Param("travelerId") UUID travelerId,
                                               @Param("limit") int limit,
                                               @Param("offset") int offset);

    /** Requests the guide never answered. */
    List<GuideBooking> findExpiredRequests(@Param("limit") int limit);

    /** Completed bookings past the complaint window and not frozen by a dispute. */
    List<GuideBooking> findPayableBookings(@Param("releasableBefore") LocalDateTime releasableBefore,
                                           @Param("limit") int limit);

    // --- money ------------------------------------------------------------------------

    int insertPayoutEntry(GuidePayoutEntry entry);

    List<GuidePayoutEntry> findPayoutEntries(@Param("guideId") UUID guideId,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    // --- reviews ----------------------------------------------------------------------

    int insertReview(GuideReview review);

    int respondToReview(@Param("id") UUID id,
                        @Param("guideId") UUID guideId,
                        @Param("response") String response);

    GuideReview findReviewByBooking(@Param("bookingId") UUID bookingId);

    List<GuideReview> findReviewsByGuide(@Param("guideId") UUID guideId,
                                         @Param("limit") int limit,
                                         @Param("offset") int offset);

    // --- analytics --------------------------------------------------------------------

    int recordServiceView(@Param("serviceId") UUID serviceId, @Param("inquiry") boolean inquiry);

    Map<String, Object> guidePerformance(@Param("guideId") UUID guideId,
                                         @Param("from") LocalDateTime from);
}
