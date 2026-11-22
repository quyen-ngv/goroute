package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupEffectiveStatus;
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
import java.util.List;
import java.util.UUID;

/**
 * Everything a reviewer needs on one meetup, including the true location and the displayed one,
 * the host's history, every participant row and the user reports. Approve and reject send
 * {@code dataVersion} back as {@code expectedVersion}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMeetupDetail {

    private UUID id;
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
    private String timezone;
    private Integer minAge;
    private Integer maxAge;
    private MeetupJoinPolicy joinPolicy;
    private Integer maxParticipants;
    private MeetupStatus status;
    private MeetupEffectiveStatus effectiveStatus;
    private MeetupRejectReason rejectReason;
    /** Reject note, or the removal reason after a remove. */
    private String reviewNote;
    private UUID reviewedBy;
    private LocalDateTime reviewedAt;
    private LocalDateTime submittedAt;
    private Integer resubmitCount;
    private Integer revision;
    private String cancelReason;
    private LocalDateTime cancelledAt;
    private long joinedCount;
    private long pendingRequestCount;
    private AdminMeetupHost host;
    private List<MeetupParticipantResponse> participants;
    private Reports reports;
    private Long dataVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Reports {
        private long count;
        /** Up to five, newest first. */
        private List<Report> latest;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Report {
        private String reason;
        private String note;
        private LocalDateTime createdAt;
    }
}
