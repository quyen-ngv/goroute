package com.ds.goroute.meetup.dto;

import com.ds.goroute.annotations.ModeratedText;
import com.ds.goroute.type.MeetupCategory;
import com.ds.goroute.type.MeetupJoinPolicy;
import com.ds.goroute.type.MeetupLocationMode;
import com.ds.goroute.type.MeetupTimeMode;
import com.ds.goroute.type.ModeratedContentType;
import com.ds.goroute.type.ModerationVisibility;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Create ({@code POST /v1/api/meetups}) or edit/resubmit ({@code PUT /v1/api/meetups/{id}},
 * where {@code expectedVersion} is required). Ages, the AREA radius, the day and the time are
 * checked by the service, which answers with the meetup error codes rather than a generic 400.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MeetupUpsertRequest {

    /** The text after "muốn ..." ("wants to ..."). */
    @NotBlank
    @Size(min = 5, max = 120)
    @ModeratedText(contentType = ModeratedContentType.MEETUP, visibility = ModerationVisibility.PUBLIC)
    private String title;

    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.MEETUP, visibility = ModerationVisibility.PUBLIC)
    private String description;

    @NotNull
    private MeetupCategory category;

    @NotNull
    private MeetupLocationMode locationMode;

    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    private BigDecimal latitude;

    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    private BigDecimal longitude;

    /** AREA only: 300, 1000 or 3000. Ignored for EXACT. */
    private Integer radiusM;

    private UUID placeId;

    @Size(max = 200)
    @ModeratedText(contentType = ModeratedContentType.MEETUP, visibility = ModerationVisibility.PUBLIC)
    private String placeName;

    @Size(max = 500)
    @ModeratedText(contentType = ModeratedContentType.MEETUP, visibility = ModerationVisibility.PUBLIC)
    private String address;

    /** Local Vietnamese date, {@code yyyy-MM-dd}. */
    @NotNull
    private LocalDate meetupDate;

    @NotNull
    private MeetupTimeMode timeMode;

    /** EXACT only: local Vietnamese time, {@code HH:mm}. Ignored for FLEXIBLE. */
    @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$")
    private String startTime;

    @NotNull
    private Integer minAge;

    /** {@code null} means "80+", no upper bound. */
    private Integer maxAge;

    @NotNull
    private MeetupJoinPolicy joinPolicy;

    /** Excluding the host, 1..50; {@code null} means unlimited. */
    private Integer maxParticipants;

    /** Required on PUT: the {@code dataVersion} the client last read. */
    private Long expectedVersion;
}
