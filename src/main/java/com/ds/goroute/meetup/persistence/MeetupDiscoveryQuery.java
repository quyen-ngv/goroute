package com.ds.goroute.meetup.persistence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * The map/list query of the Home "Hoạt động" tab: approved meetups whose window is still open,
 * within {@code radiusKm} of a centre (bounding box first, haversine second, both on the
 * displayed point), never taken down, and with no block in either direction between the viewer
 * and the host.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupDiscoveryQuery {

    private UUID viewerId;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private double radiusKm;
    private BigDecimal minLat;
    private BigDecimal maxLat;
    private BigDecimal minLng;
    private BigDecimal maxLng;
    /** Category names; empty or null means every category. */
    private List<String> categories;
    private LocalDateTime now;
    private int limit;
    private int offset;
}
