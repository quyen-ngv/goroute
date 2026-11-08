package com.ds.goroute.dto.response;

import com.ds.goroute.type.CheckinLocationSource;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One check-in waiting for a catalogue place, as the place-linking agent reads it
 * ({@code GET /admin/checkins/unlinked}). Nothing about the author: the agent needs only what
 * names the spot.
 */
@Data
@Builder
public class AdminUnlinkedCheckinResponse {
    private UUID id;
    private UUID tripId;
    private UUID activityId;
    private CheckinLocationSource locationSource;
    private String locationName;
    private String customName;
    private String ward;
    private String province;
    private String provinceCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String caption;
    /** Read only when the name and caption do not settle the place. */
    private List<String> photoUrls;
    private LocalDateTime createdAt;
}
