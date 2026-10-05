package com.ds.goroute.meetup.dto;

import com.ds.goroute.type.MeetupRejectReason;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.List;
import java.util.UUID;

/**
 * The meetup sheet/screen: every summary field plus the text and the viewer-specific parts.
 * Host-only fields ({@code pendingRequestCount}, {@code resubmitsLeft}, {@code rejectReason},
 * {@code reviewNote}) are null for everyone else. Exposes {@code getId()} so the moderation
 * advice can link a flag raised on create/update to the new row.
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class MeetupDetailResponse extends MeetupSummaryResponse {

    private String description;
    private String address;
    /**
     * The catalogue place an EXACT meetup was picked from; null when the location is hidden, like
     * {@code address}. The edit form sends it back, so a host edit keeps the link.
     */
    private UUID placeId;
    private boolean participantsVisible;
    /** Up to eight JOINED people, oldest first; empty when {@code participantsVisible} is false. */
    private List<MeetupPersonResponse> participantsPreview;
    private Long pendingRequestCount;
    private MeetupRejectReason rejectReason;
    private String reviewNote;
    private Integer resubmitsLeft;
    private String cancelReason;
    private Long dataVersion;
}
