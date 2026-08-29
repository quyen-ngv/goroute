package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateGuideBookingRequest;
import com.ds.goroute.dto.response.GuideBookingResponse;
import com.ds.goroute.entity.GuideAvailability;
import com.ds.goroute.entity.GuideBooking;
import com.ds.goroute.entity.GuidePayoutEntry;
import com.ds.goroute.entity.GuideProfile;
import com.ds.goroute.entity.GuideReview;
import com.ds.goroute.entity.GuideService;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.GuideMapper;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.GuideBookingService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.guide.GuideFeeCalculator;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.GuideBookingStatus;
import com.ds.goroute.type.GuidePaymentStatus;
import com.ds.goroute.type.GuidePayoutEntryType;
import com.ds.goroute.type.GuideServiceStatus;
import com.ds.goroute.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GuideBookingServiceImpl implements GuideBookingService {

    private static final int MAX_PAGE_SIZE = 50;

    private final GuideMapper guideMapper;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final GuideFeeCalculator feeCalculator;
    private final BusinessConfigService config;

    @Override
    @Transactional
    public GuideBookingResponse request(UUID travelerId, CreateGuideBookingRequest request) {
        requireFeatureEnabled();

        GuideBooking replayed = guideMapper.findBookingByIdempotencyKey(travelerId, request.getIdempotencyKey());
        if (replayed != null) {
            return toResponse(replayed, travelerId);
        }

        GuideService service = guideMapper.findServiceById(request.getServiceId());
        if (service == null || service.getStatus() != GuideServiceStatus.LISTED) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Service not found");
        }
        GuideProfile guide = guideMapper.findProfileById(service.getGuideId());
        if (guide == null || !guide.getStatus().isPubliclyVisible()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Service not found");
        }
        if (guide.getUserId().equals(travelerId)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "You cannot book yourself");
        }

        requireNoticePeriodMet(service, request);
        requireDateOpen(guide.getId(), request);
        requireCapacity(guide.getId(), service, request);

        GuideFeeCalculator.Breakdown fee = feeCalculator.calculate(
                service.getPricingMode(), service.getPriceAmount(), request.getGuestCount());
        LocalDateTime now = LocalDateTime.now();

        GuideBooking booking = GuideBooking.builder()
                .id(UUID.randomUUID())
                .serviceId(service.getId())
                .guideId(guide.getId())
                .travelerId(travelerId)
                .bookingDate(request.getBookingDate())
                .guestCount(request.getGuestCount())
                .travelerNote(request.getTravelerNote())
                .currency(service.getCurrency())
                // Copied, never referenced. A later price or commission change applies to
                // the next booking, not to one somebody already agreed to.
                .serviceAmount(fee.serviceAmount())
                .platformFeePercent(fee.platformFeePercent())
                .platformFeeAmount(fee.platformFeeAmount())
                .guidePayoutAmount(fee.guidePayoutAmount())
                .feeRuleVersion(fee.feeRuleVersion())
                .status(GuideBookingStatus.REQUESTED)
                .paymentStatus(GuidePaymentStatus.NONE)
                .respondBy(now.plusHours(config.getInt(BusinessConfigKey.GUIDE_RESPONSE_DEADLINE_HOURS)))
                .payoutFrozen(false)
                .idempotencyKey(request.getIdempotencyKey())
                .createdAt(now)
                .updatedAt(now)
                .build();
        guideMapper.insertBooking(booking);
        guideMapper.recordServiceView(service.getId(), true);

        notify(guide.getUserId(), NotificationType.MARKETPLACE_BOOKING_REQUEST,
                "Bạn có yêu cầu đặt lịch mới",
                "Một khách vừa gửi yêu cầu cho dịch vụ của bạn.", booking);
        return toResponse(booking, travelerId);
    }

    @Override
    @Transactional
    public GuideBookingResponse respond(UUID guideUserId, UUID bookingId, boolean accept, String reason) {
        GuideBooking booking = requireBooking(bookingId);
        requireGuideOwns(guideUserId, booking);

        GuideBookingStatus next = accept ? GuideBookingStatus.ACCEPTED : GuideBookingStatus.DECLINED;
        transition(booking, next, accept ? GuidePaymentStatus.HELD : null, reason);

        if (accept) {
            // The money is held, not paid. Neither side has it until the service is done.
            writeLedgerEntry(booking, GuidePayoutEntryType.HOLD, booking.getServiceAmount(),
                    "hold:" + booking.getId(), null, null);
        }
        notify(booking.getTravelerId(),
                accept ? NotificationType.MARKETPLACE_BOOKING_CONFIRMED : NotificationType.MARKETPLACE_BOOKING_DECLINED,
                accept ? "Hướng dẫn viên đã nhận lịch của bạn" : "Hướng dẫn viên không nhận được lịch này",
                reason, booking);
        return toResponse(requireBooking(bookingId), guideUserId);
    }

    @Override
    @Transactional
    public GuideBookingResponse confirm(UUID travelerId, UUID bookingId) {
        GuideBooking booking = requireBooking(bookingId);
        if (!booking.getTravelerId().equals(travelerId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "This is not your booking");
        }
        transition(booking, GuideBookingStatus.CONFIRMED, GuidePaymentStatus.CAPTURED, null);
        writeLedgerEntry(booking, GuidePayoutEntryType.CAPTURE, booking.getServiceAmount(),
                "capture:" + booking.getId(), null, null);
        writeLedgerEntry(booking, GuidePayoutEntryType.PLATFORM_FEE, booking.getPlatformFeeAmount().negate(),
                "fee:" + booking.getId(), null, null);
        return toResponse(requireBooking(bookingId), travelerId);
    }

    @Override
    @Transactional
    public GuideBookingResponse cancel(UUID actorId, UUID bookingId, String reason) {
        GuideBooking booking = requireBooking(bookingId);
        GuideProfile guide = guideMapper.findProfileById(booking.getGuideId());
        boolean isParticipant = booking.getTravelerId().equals(actorId)
                || (guide != null && guide.getUserId().equals(actorId));
        if (!isParticipant) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "This is not your booking");
        }

        transition(booking, GuideBookingStatus.CANCELLED, GuidePaymentStatus.REFUNDED, reason);
        if (booking.getPaymentStatus() != GuidePaymentStatus.NONE) {
            // A refund is its own entry pointing back at what it reverses; nothing that was
            // already written is changed.
            writeLedgerEntry(booking, GuidePayoutEntryType.REFUND, booking.getServiceAmount().negate(),
                    "refund:" + booking.getId(), null, reason);
        }
        return toResponse(requireBooking(bookingId), actorId);
    }

    @Override
    @Transactional
    public GuideBookingResponse complete(UUID guideUserId, UUID bookingId) {
        GuideBooking booking = requireBooking(bookingId);
        requireGuideOwns(guideUserId, booking);
        transition(booking, GuideBookingStatus.COMPLETED, null, null);
        guideMapper.refreshProfileStats(booking.getGuideId());
        return toResponse(requireBooking(bookingId), guideUserId);
    }

    @Override
    @Transactional
    public GuideBookingResponse openDispute(UUID actorId, UUID bookingId, String reason) {
        GuideBooking booking = requireBooking(bookingId);
        GuideProfile guide = guideMapper.findProfileById(booking.getGuideId());
        boolean isParticipant = booking.getTravelerId().equals(actorId)
                || (guide != null && guide.getUserId().equals(actorId));
        if (!isParticipant) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "This is not your booking");
        }
        transition(booking, GuideBookingStatus.DISPUTED, null, reason);
        // Freezing here is the point of the whole state: money must not leave while the
        // disagreement is open, because there is no way to pull it back afterwards.
        guideMapper.freezePayout(bookingId, true);
        return toResponse(requireBooking(bookingId), actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public GuideBookingResponse get(UUID actorId, UUID bookingId) {
        GuideBooking booking = requireBooking(bookingId);
        GuideProfile guide = guideMapper.findProfileById(booking.getGuideId());
        boolean isParticipant = booking.getTravelerId().equals(actorId)
                || (guide != null && guide.getUserId().equals(actorId));
        if (!isParticipant) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Booking not found");
        }
        return toResponse(booking, actorId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideBookingResponse> forGuide(UUID guideUserId, GuideBookingStatus status,
                                               int page, int size) {
        GuideProfile guide = requireGuideProfile(guideUserId);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return guideMapper.findBookingsForGuide(guide.getId(), status == null ? null : status.name(),
                        safeSize, Math.max(0, page) * safeSize).stream()
                .map(booking -> toResponse(booking, guideUserId))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideBookingResponse> forTraveler(UUID travelerId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return guideMapper.findBookingsForTraveler(travelerId, safeSize, Math.max(0, page) * safeSize)
                .stream()
                .map(booking -> toResponse(booking, travelerId))
                .toList();
    }

    @Override
    @Transactional
    public GuideReview review(UUID travelerId, UUID bookingId, int rating, String comment) {
        GuideBooking booking = requireBooking(bookingId);
        if (!booking.getTravelerId().equals(travelerId)) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "This is not your booking");
        }
        // Only a completed booking can be reviewed. That constraint is the entire value of
        // the rating: it means everybody who rated actually went.
        if (booking.getStatus() != GuideBookingStatus.COMPLETED) {
            throw new BusinessException(ErrorConstant.GUIDE_BOOKING_STATE_INVALID,
                    "You can review a guide after the service is completed.");
        }

        LocalDateTime now = LocalDateTime.now();
        GuideReview review = GuideReview.builder()
                .id(UUID.randomUUID())
                .bookingId(bookingId)
                .guideId(booking.getGuideId())
                .travelerId(travelerId)
                .rating(rating)
                .comment(comment)
                .isRemoved(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        if (guideMapper.insertReview(review) == 0) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "You have already reviewed this booking");
        }
        guideMapper.refreshProfileStats(booking.getGuideId());
        return guideMapper.findReviewByBooking(bookingId);
    }

    @Override
    @Transactional
    public GuideReview respondToReview(UUID guideUserId, UUID reviewId, String response) {
        GuideProfile guide = requireGuideProfile(guideUserId);
        if (guideMapper.respondToReview(reviewId, guide.getId(), response) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Review not found");
        }
        return guideMapper.findReviewsByGuide(guide.getId(), MAX_PAGE_SIZE, 0).stream()
                .filter(review -> review.getId().equals(reviewId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "Review not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuideReview> reviewsForGuide(UUID guideId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return guideMapper.findReviewsByGuide(guideId, safeSize, Math.max(0, page) * safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> performance(UUID guideUserId, int days) {
        GuideProfile guide = requireGuideProfile(guideUserId);
        return guideMapper.guidePerformance(guide.getId(),
                LocalDateTime.now().minusDays(Math.max(1, Math.min(days, 365))));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GuidePayoutEntry> payoutLedger(UUID guideUserId, int page, int size) {
        GuideProfile guide = requireGuideProfile(guideUserId);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return guideMapper.findPayoutEntries(guide.getId(), safeSize, Math.max(0, page) * safeSize);
    }

    @Override
    @Transactional
    public int expireStaleRequests(int batchSize) {
        int expired = 0;
        for (GuideBooking booking : guideMapper.findExpiredRequests(Math.max(1, Math.min(batchSize, 500)))) {
            if (guideMapper.updateBookingStatus(booking.getId(), GuideBookingStatus.EXPIRED.name(),
                    GuideBookingStatus.REQUESTED.name(), null, "The guide did not respond in time") == 1) {
                expired++;
                notify(booking.getTravelerId(), NotificationType.MARKETPLACE_BOOKING_DECLINED,
                        "Yêu cầu đặt lịch đã hết hiệu lực",
                        "Hướng dẫn viên chưa phản hồi trong thời hạn, yêu cầu của bạn đã hết hiệu lực.",
                        booking);
            }
        }
        return expired;
    }

    @Override
    @Transactional
    public int releaseDuePayouts(int batchSize) {
        LocalDateTime releasableBefore = LocalDateTime.now()
                .minusDays(config.getInt(BusinessConfigKey.GUIDE_PAYOUT_HOLD_DAYS));
        int released = 0;

        for (GuideBooking booking : guideMapper.findPayableBookings(releasableBefore,
                Math.max(1, Math.min(batchSize, 500)))) {
            if (guideMapper.markPayoutReleased(booking.getId()) != 1) {
                continue;
            }
            writeLedgerEntry(booking, GuidePayoutEntryType.PAYOUT, booking.getGuidePayoutAmount(),
                    "payout:" + booking.getId(), null, null);
            released++;
        }
        return released;
    }

    @Override
    @Transactional
    public void freezePayout(UUID operatorId, UUID bookingId, boolean frozen) {
        requireBooking(bookingId);
        guideMapper.freezePayout(bookingId, frozen);
        log.info("Operator {} {} the payout for booking {}", operatorId,
                frozen ? "froze" : "released the freeze on", bookingId);
    }

    // --- rules -------------------------------------------------------------------------

    private void requireNoticePeriodMet(GuideService service, CreateGuideBookingRequest request) {
        int noticeHours = service.getAdvanceNoticeHours() == null ? 0 : service.getAdvanceNoticeHours();
        // Checked here, not only shown in the app: a rule the client enforces is a rule
        // anybody can skip by calling the endpoint directly.
        if (request.getBookingDate().atStartOfDay().isBefore(LocalDateTime.now().plusHours(noticeHours))) {
            throw new BusinessException(ErrorConstant.GUIDE_UNAVAILABLE_ON_DATE,
                    "This guide needs at least " + noticeHours + " hours' notice.");
        }
    }

    private void requireDateOpen(UUID guideId, CreateGuideBookingRequest request) {
        GuideAvailability availability = guideMapper.findAvailabilityForDate(guideId, request.getBookingDate());
        if (availability != null && Boolean.TRUE.equals(availability.getIsBlocked())) {
            throw new BusinessException(ErrorConstant.GUIDE_UNAVAILABLE_ON_DATE,
                    "The guide is not available on that date.");
        }
    }

    /**
     * A guide is one person and cannot lead two groups at once.
     *
     * <p>The committed total is read with the rows locked, so two people taking the last
     * places at the same moment cannot both succeed and leave the guide to discover it on
     * the day.
     */
    private void requireCapacity(UUID guideId, GuideService service, CreateGuideBookingRequest request) {
        GuideAvailability availability = guideMapper.findAvailabilityForDate(guideId, request.getBookingDate());
        int dayCapacity = availability != null && availability.getMaxGuests() != null
                ? availability.getMaxGuests()
                : service.getMaxGuests();

        int committed = orZero(guideMapper.sumCommittedGuestsForUpdate(guideId, request.getBookingDate()));
        if (committed + request.getGuestCount() > dayCapacity) {
            throw new BusinessException(ErrorConstant.GUIDE_CAPACITY_EXCEEDED,
                    "That booking exceeds the number of guests the guide accepts.");
        }
    }

    /**
     * Moves the booking, refusing a move the state machine does not allow.
     *
     * <p>The expected state is part of the update predicate, so two callers attempting the
     * same transition cannot both think they succeeded.
     */
    private void transition(GuideBooking booking, GuideBookingStatus next,
                            GuidePaymentStatus paymentStatus, String reason) {
        if (!booking.getStatus().canTransitionTo(next)) {
            throw new BusinessException(ErrorConstant.GUIDE_BOOKING_STATE_INVALID,
                    "This booking cannot move to that state.");
        }
        int updated = guideMapper.updateBookingStatus(booking.getId(), next.name(),
                booking.getStatus().name(), paymentStatus == null ? null : paymentStatus.name(), reason);
        if (updated != 1) {
            throw new BusinessException(ErrorConstant.GUIDE_BOOKING_STATE_INVALID,
                    "This booking has already changed. Reload and try again.");
        }
    }

    private void writeLedgerEntry(GuideBooking booking, GuidePayoutEntryType type, BigDecimal amount,
                                  String referenceKey, UUID reversesEntryId, String note) {
        guideMapper.insertPayoutEntry(GuidePayoutEntry.builder()
                .id(UUID.randomUUID())
                .bookingId(booking.getId())
                .guideId(booking.getGuideId())
                .entryType(type)
                .amount(amount)
                .currency(booking.getCurrency())
                .reversesEntryId(reversesEntryId)
                .referenceKey(referenceKey)
                .note(note)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private void requireFeatureEnabled() {
        if (!config.getBoolean(BusinessConfigKey.GUIDE_ENABLED)) {
            throw new BusinessException(ErrorConstant.GUIDE_FEATURE_DISABLED,
                    "The guide marketplace is currently unavailable.");
        }
    }

    private GuideBooking requireBooking(UUID bookingId) {
        GuideBooking booking = guideMapper.findBookingById(bookingId);
        if (booking == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Booking not found");
        }
        return booking;
    }

    private GuideProfile requireGuideProfile(UUID userId) {
        GuideProfile guide = guideMapper.findProfileByUser(userId);
        if (guide == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "You do not have a guide profile");
        }
        return guide;
    }

    private void requireGuideOwns(UUID guideUserId, GuideBooking booking) {
        GuideProfile guide = requireGuideProfile(guideUserId);
        if (!guide.getId().equals(booking.getGuideId())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR, "This is not your booking");
        }
    }

    private void notify(UUID userId, NotificationType type, String title, String body,
                        GuideBooking booking) {
        try {
            notificationService.createNotification(userId, null, type, title, body,
                    Map.of("bookingId", booking.getId().toString(),
                            "guideId", booking.getGuideId().toString()),
                    null);
        } catch (RuntimeException exception) {
            // A guide who does not hear about a request is a guide who loses the customer,
            // but a failed notification must not undo the booking itself.
            log.warn("Could not notify {} about booking {}: {}", userId, booking.getId(),
                    exception.getMessage());
        }
    }

    private GuideBookingResponse toResponse(GuideBooking booking, UUID viewerId) {
        GuideProfile guide = guideMapper.findProfileById(booking.getGuideId());
        GuideService service = guideMapper.findServiceById(booking.getServiceId());
        User traveler = userRepository.findById(booking.getTravelerId()).orElse(null);

        return GuideBookingResponse.builder()
                .id(booking.getId())
                .serviceId(booking.getServiceId())
                .serviceTitle(service == null ? null : service.getTitle())
                .guideId(booking.getGuideId())
                .guideDisplayName(guide == null ? null : guide.getDisplayName())
                .travelerId(booking.getTravelerId())
                .travelerDisplayName(traveler == null ? null : traveler.getFullName())
                .bookingDate(booking.getBookingDate())
                .guestCount(booking.getGuestCount())
                .travelerNote(booking.getTravelerNote())
                .currency(booking.getCurrency())
                .serviceAmount(booking.getServiceAmount())
                // Both sides see the commission and the guide's share. A platform that
                // hides its cut invites the next booking to happen off-platform.
                .platformFeePercent(booking.getPlatformFeePercent())
                .platformFeeAmount(booking.getPlatformFeeAmount())
                .guidePayoutAmount(booking.getGuidePayoutAmount())
                .travelerTotal(booking.getServiceAmount())
                .status(booking.getStatus())
                .paymentStatus(booking.getPaymentStatus())
                .respondBy(booking.getRespondBy())
                .confirmedAt(booking.getConfirmedAt())
                .completedAt(booking.getCompletedAt())
                .payoutReleasedAt(booking.getPayoutReleasedAt())
                .payoutFrozen(Boolean.TRUE.equals(booking.getPayoutFrozen()))
                .createdAt(booking.getCreatedAt())
                .reviewable(booking.getStatus() == GuideBookingStatus.COMPLETED
                        && booking.getTravelerId().equals(viewerId)
                        && guideMapper.findReviewByBooking(booking.getId()) == null)
                .build();
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
