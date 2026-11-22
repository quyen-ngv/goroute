package com.ds.goroute.meetup.domain;

import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupRejectReason;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupTimeMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row of {@code meetups}. {@code latitude/longitude} is the true point;
 * {@code displayLatitude/displayLongitude} is what strangers see. Times are UTC;
 * {@code meetupDate} is the local Vietnamese date.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Meetup {

    private UUID id;
    private UUID hostUserId;
    private String title;
    private String description;
    private MeetupCategory category;
    private MeetupLocationMode locationMode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private BigDecimal displayLatitude;
    private BigDecimal displayLongitude;
    private Integer radiusM;
    private UUID placeId;
    private String placeName;
    private String address;
    private String areaLabel;
    private String wardCode;
    private String provinceCode;
    private LocalDate meetupDate;
    private MeetupTimeMode timeMode;
    private LocalDateTime startsAt;
    private LocalDateTime visibleUntil;
    private Integer minAge;
    private Integer maxAge;
    private MeetupJoinPolicy joinPolicy;
    private Integer maxParticipants;
    private MeetupStatus status;
    private MeetupRejectReason rejectReason;
    private String reviewNote;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime submittedAt;
    private Integer resubmitCount;
    private Integer revision;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Past = its window has closed; never stored, always computed against the caller's clock. */
    public boolean isPast(LocalDateTime nowUtc) {
        return visibleUntil != null && !visibleUntil.isAfter(nowUtc);
    }
}
