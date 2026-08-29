package com.ds.goroute.dto.response;

import com.ds.goroute.type.GuideProfileStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A guide profile.
 *
 * <p>Two shapes from one type: the public view leaves the contact fields, the decision
 * notes and the plan null. The filtering happens in the service, not in the screen --
 * filtering at the display layer is the most common way private data leaks.
 */
@Data
@Builder
public class GuideProfileResponse {
    private UUID id;
    private UUID userId;
    private String displayName;
    private String headline;
    private String bio;
    private List<String> languages;
    private List<String> areaProvinceCodes;
    private Integer yearsExperience;
    private String avatarUrl;

    private GuideProfileStatus status;
    private String decisionNote;
    private String informationRequested;

    private String contactPhone;
    private String contactEmail;
    private String plan;
    private LocalDateTime planExpiresAt;

    private Integer responseMinutesAvg;
    private Integer completedBookings;
    /** Withheld until there are enough reviews for an average to mean anything. */
    private BigDecimal ratingAverage;
    private Integer ratingCount;

    private LocalDateTime submittedAt;
    private LocalDateTime createdAt;
}
