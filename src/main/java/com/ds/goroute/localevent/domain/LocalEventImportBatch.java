package com.ds.goroute.localevent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** One {@code local_event_import_batches} row, plus the actor's and key's display names when read. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocalEventImportBatch {

    private UUID id;
    private UUID actorUserId;
    private UUID apiKeyId;
    private Boolean dryRun;
    private Integer total;
    private Integer created;
    private Integer updated;
    private Integer unchanged;
    private Integer skipped;
    private Integer failed;
    private LocalDateTime createdAt;

    /** Read-only: users.full_name of the actor. */
    private String actorName;
    /** Read-only: admin_api_keys.name of the key used, if any. */
    private String apiKeyName;
}
