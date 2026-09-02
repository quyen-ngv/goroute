package com.ds.goroute.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicUserProfileResponse {
    private UUID id;
    private String fullName;
    private String username;
    private String avatarUrl;
    private String bio;
    private Map<String, String> socialLinks;
    private int tripCount;
    private int reviewCount;
    private int likeCount;
    private int cloneCount;
    private int contributionCount;
    private int hiddenGemContributionCount;
    private int followersCount;
    private int followingCount;
    private UserRankProgressResponse rank;

    /** True when an operator has vouched for this account as a VietdeGuide. */
    private boolean guide;

    /** What the guide badge says, when the guide has a speciality worth naming. */
    private String guideTitle;
    private boolean isFollowing;
    private boolean isFollowedBy;
}
