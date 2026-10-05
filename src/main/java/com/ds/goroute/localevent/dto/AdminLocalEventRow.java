package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventCategory;
import com.ds.goroute.type.LocalEventPublishStatus;
import com.ds.goroute.type.LocalEventStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row of GET /v1/api/admin/events. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminLocalEventRow {

    private UUID id;
    private String title;
    private LocalEventCategory category;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private String venueName;
    private String provinceCode;
    private String coverUrl;
    private LocalEventPublishStatus publishStatus;
    private LocalEventStatus eventStatus;
    private String source;
    private String externalId;
    private UUID duplicateOfEventId;
    private Boolean manuallyEdited;
    private Integer pendingImages;
    private LocalDateTime updatedAt;
}
