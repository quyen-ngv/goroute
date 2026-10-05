package com.ds.goroute.localevent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Result of POST /v1/api/admin/events/import. {@code skipped} counts SKIPPED_EDITED and
 * SKIPPED_DELETED. A dry run computes the same results without writing ({@code batchId} null).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImportResponse {

    private UUID batchId;
    private boolean dryRun;
    private int total;
    private int created;
    private int updated;
    private int unchanged;
    private int skipped;
    private int failed;
    private List<LocalEventImportItemResult> items;
}
