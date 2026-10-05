package com.ds.goroute.localevent.dto;

import com.ds.goroute.type.LocalEventImportResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** What the import did with item {@code index} (0-based position in the request's events array). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImportItemResult {

    private int index;
    private String source;
    private String externalId;
    private LocalEventImportResult result;
    /** The created/updated/matched event; null for FAILED (and for CREATED on a dry run). */
    private UUID eventId;
    /** CREATED only: an existing event this one probably duplicates. */
    private UUID duplicateOfEventId;
    /** Empty unless FAILED. */
    private List<LocalEventFieldError> errors;
}
