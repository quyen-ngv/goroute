package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.IcalFeedLinkResponse;
import com.ds.goroute.entity.MarketplaceIcalBooking;
import com.ds.goroute.entity.MarketplaceIcalRoom;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.MarketplaceIcalMapper;
import com.ds.goroute.service.IcalFeedService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.utils.IcalWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@Slf4j
public class IcalFeedServiceImpl implements IcalFeedService {

    static final String FEED_PATH = "/v1/api/public/ical/rooms/";
    static final String FEED_SUFFIX = ".ics";
    static final String UID_DOMAIN = "@goroute";
    /** 36 random bytes -> 48 url-safe base64 characters without padding. */
    private static final int TOKEN_BYTES = 36;
    private static final Pattern TOKEN_SHAPE = Pattern.compile("^[A-Za-z0-9_-]{16,64}$");
    /** Feed window: recent past so departing guests stay visible, and the full 730-day inventory horizon ahead. */
    private static final int PAST_DAYS = 30;
    private static final int FUTURE_DAYS = 730;
    private static final int MAX_BOOKINGS = 5000;
    private static final int MAX_STOP_SELL_DAYS = PAST_DAYS + FUTURE_DAYS + 1;

    private final MarketplaceIcalMapper mapper;
    private final PartnerAuthorizationService authorization;
    private final String publicBaseUrl;
    private final SecureRandom secureRandom = new SecureRandom();

    public IcalFeedServiceImpl(MarketplaceIcalMapper mapper,
                               PartnerAuthorizationService authorization,
                               @Value("${goroute.public-base-url:http://localhost:8080}") String publicBaseUrl) {
        this.mapper = mapper;
        this.authorization = authorization;
        this.publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    @Transactional
    public IcalFeedLinkResponse getFeedLink(UUID actorUserId, UUID roomTypeId) {
        MarketplaceIcalRoom room = requireRoom(roomTypeId);
        authorization.requireResourcePermission(room.getOrganizationId(), actorUserId, "HOTEL", room.getHotelId(), "HOTEL_READ");
        if (room.getIcalToken() == null) {
            String token = newToken();
            if (mapper.assignTokenIfMissing(roomTypeId, token) == 1) {
                room.setIcalToken(token);
            } else {
                // A concurrent request created it first; use theirs instead of overwriting it.
                room = requireRoom(roomTypeId);
            }
        }
        return toLink(room);
    }

    @Override
    @Transactional
    public IcalFeedLinkResponse rotateFeedLink(UUID actorUserId, UUID roomTypeId) {
        MarketplaceIcalRoom room = requireRoom(roomTypeId);
        authorization.requireResourcePermission(room.getOrganizationId(), actorUserId, "HOTEL", room.getHotelId(), "INVENTORY_WRITE");
        String token = newToken();
        mapper.replaceToken(roomTypeId, token);
        room.setIcalToken(token);
        log.info("iCal feed token rotated for room {} by user {}", roomTypeId, actorUserId);
        return toLink(room);
    }

    @Override
    @Transactional(readOnly = true)
    public String renderFeed(String token) {
        // The token is the only credential; an unknown or malformed one is indistinguishable from a missing room.
        MarketplaceIcalRoom room = token != null && TOKEN_SHAPE.matcher(token).matches()
                ? mapper.findRoomByToken(token) : null;
        if (room == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Calendar not found");
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate from = today.minusDays(PAST_DAYS);
        List<MarketplaceIcalBooking> stays = mapper.findBookedStays(room.getRoomTypeId(), from, MAX_BOOKINGS);
        List<LocalDate> closedDays = mapper.findStopSellDays(room.getRoomTypeId(), from, today.plusDays(FUTURE_DAYS), MAX_STOP_SELL_DAYS);
        List<IcalWriter.Event> events = buildEvents(room.getRoomTypeId(), stays, closedDays);
        String name = room.getHotelName() == null ? room.getRoomName() : room.getHotelName() + " - " + room.getRoomName();
        return IcalWriter.render(name, events, LocalDateTime.now(ZoneOffset.UTC));
    }

    /**
     * One event per stay (DTEND = check-out, exclusive) followed by one event per run of consecutive
     * stop-sell days. Package-private so the mapping is unit-testable without a database.
     */
    static List<IcalWriter.Event> buildEvents(UUID roomTypeId, List<MarketplaceIcalBooking> stays, List<LocalDate> closedDays) {
        List<IcalWriter.Event> events = new ArrayList<>();
        for (MarketplaceIcalBooking stay : stays) {
            if (stay.getCheckInDate() == null) {
                continue;
            }
            LocalDate end = stay.getCheckOutDate() == null || !stay.getCheckOutDate().isAfter(stay.getCheckInDate())
                    ? stay.getCheckInDate().plusDays(1) : stay.getCheckOutDate();
            String code = stay.getBookingCode() == null ? stay.getBookingId().toString() : stay.getBookingCode();
            events.add(new IcalWriter.Event(stay.getBookingId() + UID_DOMAIN, stay.getCheckInDate(), end, "Booked - " + code));
        }
        LocalDate runStart = null;
        LocalDate runEnd = null;
        for (LocalDate day : closedDays) {
            if (day == null) {
                continue;
            }
            if (runEnd != null && day.equals(runEnd.plusDays(1))) {
                runEnd = day;
                continue;
            }
            if (runStart != null) {
                events.add(closedEvent(roomTypeId, runStart, runEnd));
            }
            runStart = day;
            runEnd = day;
        }
        if (runStart != null) {
            events.add(closedEvent(roomTypeId, runStart, runEnd));
        }
        return events;
    }

    private static IcalWriter.Event closedEvent(UUID roomTypeId, LocalDate start, LocalDate lastDay) {
        return new IcalWriter.Event("closed-" + start + "-" + roomTypeId + UID_DOMAIN, start, lastDay.plusDays(1), "Closed");
    }

    private MarketplaceIcalRoom requireRoom(UUID roomTypeId) {
        MarketplaceIcalRoom room = roomTypeId == null ? null : mapper.findRoomById(roomTypeId);
        if (room == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Room type not found");
        }
        return room;
    }

    private IcalFeedLinkResponse toLink(MarketplaceIcalRoom room) {
        return IcalFeedLinkResponse.builder()
                .roomTypeId(room.getRoomTypeId())
                .token(room.getIcalToken())
                .url(publicBaseUrl + FEED_PATH + room.getIcalToken() + FEED_SUFFIX)
                .build();
    }

    private String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
