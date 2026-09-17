package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinLocationSource;
import com.ds.goroute.type.ContentVisibility;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** One row of the admin check-in console: enough to judge a hide/show decision without
 *  opening the app. */
@Data
@Builder
public class AdminCheckinResponse {

    private UUID id;
    private UUID userId;
    private String userDisplayName;
    private String userEmail;
    private String userAvatarUrl;

    private UUID placeId;
    private String placeName;

    /** The catalogue row's address, so an operator can tell whether the link is right. */
    private String placeAddress;

    private String locationName;
    private String customName;

    /** What the author's device recorded: the administrative parts and the raw point. */
    private String ward;
    private String district;
    private String province;
    private String provinceCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private CheckinLocationSource locationSource;
    private String locationKey;

    /** True when an operator has already moved this check-in at least once. */
    private boolean locationReassigned;

    private String caption;
    private List<String> photoUrls;
    private Integer overallRating;

    private ContentVisibility visibility;
    private int likeCount;

    /** True when a moderation takedown currently hides this check-in from public view. */
    private boolean hidden;

    private LocalDateTime createdAt;
}
