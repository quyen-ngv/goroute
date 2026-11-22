package com.ds.goroute.localevent.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.List;

/** GET /v1/api/admin/events/import-batches/{id}: the batch and the result of every item. */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class LocalEventImportBatchDetailResponse extends LocalEventImportBatchResponse {

    private List<LocalEventImportItemResult> items;
}
