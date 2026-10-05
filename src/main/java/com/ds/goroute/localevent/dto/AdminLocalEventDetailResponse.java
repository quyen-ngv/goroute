package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventPublishStatus;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * GET /v1/api/admin/events/{id} and /lookup: the public detail plus the editorial and import
 * metadata, every image with its state, and {@code dataVersion} for the next edit.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class AdminLocalEventDetailResponse extends LocalEventDetailFields {

    private LocalEventPublishStatus publishStatus;
    private LocalDateTime publishedAt;
    private String externalId;
    private LocalDateTime sourceUpdatedAt;
    private String contentHash;
    private BigDecimal extractionConfidence;
    private String extractedBy;
    private JsonNode rawPayload;
    private UUID duplicateOfEventId;
    private LocalEventDuplicateRef duplicateOf;
    private Boolean manuallyEdited;
    /** Only the crawler's lookup can return a deleted event; every other read answers 404. */
    private Boolean deleted;
    private List<AdminLocalEventImageResponse> images;
    private UUID createdBy;
    private UUID updatedBy;
    private LocalDateTime createdAt;
    private Long dataVersion;
}
