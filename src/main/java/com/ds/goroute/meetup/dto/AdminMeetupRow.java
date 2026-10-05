package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupEffectiveStatus;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupStatus;
import com.ds.goroute.type.MeetupTimeMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** One row of the console meetup list and review queue. {@code visibleUntil} is the review deadline. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminMeetupRow {

    private UUID id;
    private String title;
    private MeetupCategory category;
    private UUID hostId;
    private String hostName;
    private String areaLabel;
    private String provinceCode;
    private MeetupLocationMode locationMode;
    private LocalDate meetupDate;
    private MeetupTimeMode timeMode;
    private LocalDateTime startsAt;
    private LocalDateTime visibleUntil;
    private MeetupStatus status;
    private MeetupEffectiveStatus effectiveStatus;
    private MeetupJoinPolicy joinPolicy;
    private long joinedCount;
    private Integer revision;
    private Integer resubmitCount;
    private LocalDateTime submittedAt;
    private long reportCount;
    private Long dataVersion;
}
