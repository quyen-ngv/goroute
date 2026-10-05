package com.ds.goroute.localevent.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.localevent.domain.LocalEventImportBatch;
import com.ds.goroute.localevent.domain.LocalEventImportItem;
import com.ds.goroute.localevent.dto.LocalEventImportBatchDetailResponse;
import com.ds.goroute.localevent.dto.LocalEventImportBatchResponse;
import com.ds.goroute.localevent.dto.LocalEventImportItemResult;
import com.ds.goroute.localevent.persistence.LocalEventImportRepository;
import com.ds.goroute.type.LocalEventImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** The audit trail of non-dry-run imports: who sent what, and what happened to each item. */
@Service
@RequiredArgsConstructor
public class LocalEventImportBatchService {

    private final LocalEventImportRepository repository;
    private final LocalEventResponses responses;

    @Transactional(readOnly = true)
    public PageResponse<LocalEventImportBatchResponse> list(int page, int size) {
        List<LocalEventImportBatchResponse> items = repository.findBatches(size, page * size).stream()
                .map(batch -> fill(new LocalEventImportBatchResponse(), batch))
                .toList();
        return PageResponse.of(items, repository.countBatches(), page, size);
    }

    @Transactional(readOnly = true)
    public LocalEventImportBatchDetailResponse detail(UUID batchId) {
        LocalEventImportBatch batch = repository.findBatchById(batchId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.LOCAL_EVENT_IMPORT_BATCH_NOT_FOUND,
                        "Import batch not found"));
        LocalEventImportBatchDetailResponse response = fill(new LocalEventImportBatchDetailResponse(), batch);
        response.setItems(repository.findItemsByBatch(batchId).stream().map(this::item).toList());
        return response;
    }

    private LocalEventImportItemResult item(LocalEventImportItem row) {
        return LocalEventImportItemResult.builder()
                .index(row.getItemIndex() == null ? 0 : row.getItemIndex())
                .source(row.getSource())
                .externalId(row.getExternalId())
                .result(LocalEventImportResult.valueOf(row.getResult()))
                .eventId(row.getEventId())
                .duplicateOfEventId(row.getDuplicateOfEventId())
                .errors(responses.errors(row.getErrors()))
                .build();
    }

    private static <T extends LocalEventImportBatchResponse> T fill(T response, LocalEventImportBatch batch) {
        response.setId(batch.getId());
        response.setActorUserId(batch.getActorUserId());
        response.setActorName(batch.getActorName());
        response.setApiKeyId(batch.getApiKeyId());
        response.setApiKeyName(batch.getApiKeyName());
        response.setDryRun(Boolean.TRUE.equals(batch.getDryRun()));
        response.setTotal(value(batch.getTotal()));
        response.setCreated(value(batch.getCreated()));
        response.setUpdated(value(batch.getUpdated()));
        response.setUnchanged(value(batch.getUnchanged()));
        response.setSkipped(value(batch.getSkipped()));
        response.setFailed(value(batch.getFailed()));
        response.setCreatedAt(batch.getCreatedAt());
        return response;
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }
}
