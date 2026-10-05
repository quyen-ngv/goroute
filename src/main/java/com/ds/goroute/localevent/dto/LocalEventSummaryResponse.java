package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventCategory;
import com.ds.goroute.type.LocalEventStatus;
import com.ds.goroute.type.LocalEventTicketAvailability;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of GET /v1/api/public/events. Times are UTC without offset (repo convention);
 * {@code timezone} is the event's own zone for day logic.
 */
@Data
@NoArgsConstructor
public class LocalEventSummaryResponse {

    private UUID id;
    private String title;
    private LocalEventCategory category;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String timezone;
    private Boolean allDay;
    private LocalEventStatus eventStatus;
    private String venueName;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    /** Kilometres from the search centre; null outside a radius search. */
    private Double distanceKm;
    private String coverUrl;
    private Boolean isFree;
    private Long priceMin;
    private Long priceMax;
    private String currency;
    private LocalEventTicketAvailability ticketAvailability;
    private Integer sessionCount;
}
