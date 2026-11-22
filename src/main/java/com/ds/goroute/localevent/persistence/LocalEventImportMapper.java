package com.ds.goroute.localevent.persistence;

import com.ds.goroute.localevent.domain.LocalEventImportBatch;
import com.ds.goroute.localevent.domain.LocalEventImportItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/** Import audit rows (resources/mapper/LocalEventImportMapper.xml). */
@Mapper
public interface LocalEventImportMapper {

    int insertBatch(LocalEventImportBatch batch);

    int insertItems(@Param("items") List<LocalEventImportItem> items);

    List<LocalEventImportBatch> findBatches(@Param("limit") int limit, @Param("offset") int offset);

    long countBatches();

    LocalEventImportBatch findBatchById(@Param("id") UUID id);

    List<LocalEventImportItem> findItemsByBatch(@Param("batchId") UUID batchId);
}
