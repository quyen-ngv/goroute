package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupParticipantStatus;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupTimeMode;
import com.ds.goroute.type.MeetupViewerRole;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A meetup card on the Home map/list and in "Của tôi". When {@code locationHidden} is true,
 * {@code latitude/longitude} is the displayed (offset) point, {@code radiusM} is the circle to
 * draw, and {@code placeName} is null. Times are UTC without offset; {@code meetupDate} and the
 * day logic are in {@code timezone}.
 */
@Data
@NoArgsConstructor
public class MeetupSummaryResponse {

    private UUID id;
    private MeetupPersonResponse host;
    private String title;
    private MeetupCategory category;
    private MeetupLocationMode locationMode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer radiusM;
    private boolean locationHidden;
    private String areaLabel;
    private String placeName;
    private LocalDate meetupDate;
    private MeetupTimeMode timeMode;
    private LocalDateTime startsAt;
    private LocalDateTime visibleUntil;
    private String timezone;
    private MeetupJoinPolicy joinPolicy;
    private Integer minAge;
    private Integer maxAge;
    private Integer maxParticipants;
    private long joinedCount;
    /** Joined people plus the host. */
    private long participantCount;
    /** From the query centre to the displayed point; null when the request had no centre. */
    private Double distanceKm;
    private MeetupStatus status;
    private MeetupEffectiveStatus effectiveStatus;
    private MeetupViewerRole viewerRole;
    private MeetupParticipantStatus viewerStatus;
    private LocalDateTime createdAt;
}
