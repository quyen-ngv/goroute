package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEventImportBatch;
import com.ds.goroute.localevent.domain.LocalEventImportItem;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence facade over {@link LocalEventImportMapper}. */
public interface LocalEventImportRepository {

    /** Writes the batch row and all of its item rows. */
    void insertBatch(LocalEventImportBatch batch, List<LocalEventImportItem> items);

    List<LocalEventImportBatch> findBatches(int limit, int offset);

    long countBatches();

    Optional<LocalEventImportBatch> findBatchById(UUID id);

    List<LocalEventImportItem> findItemsByBatch(UUID batchId);
}
