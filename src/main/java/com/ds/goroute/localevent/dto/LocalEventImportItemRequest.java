package com.ds.goroute.localevent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * One event of POST /v1/api/admin/events/import: the content fields plus where it came from.
 * {@code (source, externalId)} is the natural key every re-import is matched on.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class LocalEventImportItemRequest extends LocalEventUpsertRequest {

    private String source;
    private String externalId;
    private String sourceUpdatedAt;
    private BigDecimal extractionConfidence;
    private String extractedBy;
    private JsonNode rawPayload;
    private Boolean overwriteManualEdits;
}
