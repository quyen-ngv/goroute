package com.ds.goroute.localevent.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One stored import batch (GET /v1/api/admin/events/import-batches). */
@Data
@NoArgsConstructor
public class LocalEventImportBatchResponse {

    private UUID id;
    private UUID actorUserId;
    private String actorName;
    private UUID apiKeyId;
    private String apiKeyName;
    private boolean dryRun;
    private int total;
    private int created;
    private int updated;
    private int unchanged;
    private int skipped;
    private int failed;
    private LocalDateTime createdAt;
}
