package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEventImportBatch;
import com.ds.goroute.localevent.domain.LocalEventImportItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class LocalEventImportRepositoryImpl implements LocalEventImportRepository {

    private final LocalEventImportMapper mapper;

    /** The batch and its items are one audit record: both or neither. */
    @Override
    @Transactional
    public void insertBatch(LocalEventImportBatch batch, List<LocalEventImportItem> items) {
        mapper.insertBatch(batch);
        if (items != null && !items.isEmpty()) {
            mapper.insertItems(items);
        }
    }

    @Override
    public List<LocalEventImportBatch> findBatches(int limit, int offset) {
        return mapper.findBatches(limit, offset);
    }

    @Override
    public long countBatches() {
        return mapper.countBatches();
    }

    @Override
    public Optional<LocalEventImportBatch> findBatchById(UUID id) {
        return Optional.ofNullable(mapper.findBatchById(id));
    }

    @Override
    public List<LocalEventImportItem> findItemsByBatch(UUID batchId) {
        return mapper.findItemsByBatch(batchId);
    }
}
