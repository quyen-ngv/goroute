package com.ds.goroute.entity;

import com.ds.goroute.type.GuideProfileStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Somebody offering to show travellers around (GUIDE-01, GUIDE-02).
 *
 * <p>Contact details live here but never reach the public profile. A traveller needs
 * enough to decide whether to hire somebody, which is not the same as everything the
 * applicant handed over.
 *
 * <p>Response time and completed-booking count are computed from real bookings, never
 * self-declared: a number the guide can set is a number that stops meaning anything.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuideProfile {
    private UUID id;
    private UUID userId;
    private String displayName;
    private String headline;
    private String bio;
    private String languages;
    private String areaProvinceCodes;
    private Integer yearsExperience;
    private String avatarUrl;

    private String contactPhone;
    private String contactEmail;

    private GuideProfileStatus status;
    private LocalDateTime submittedAt;
    private LocalDateTime decidedAt;
    private UUID decidedBy;
    private String decisionNote;
    private String informationRequested;

    private String plan;
    private LocalDateTime planExpiresAt;

    private Integer responseMinutesAvg;
    private Integer completedBookings;
    private BigDecimal ratingAverage;
    private Integer ratingCount;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
