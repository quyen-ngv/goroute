package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One {@code local_event_import_items} row: the result of one item of a stored batch. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImportItem {

    private UUID id;
    private UUID batchId;
    private Integer itemIndex;
    private String source;
    private String externalId;
    private String result;
    private UUID eventId;
    private UUID duplicateOfEventId;
    /** JSON array of {field, code, message}. */
    private String errors;
    private LocalDateTime createdAt;
}
