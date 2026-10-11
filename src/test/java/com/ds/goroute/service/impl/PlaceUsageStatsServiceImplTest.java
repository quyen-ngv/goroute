package com.ds.goroute.service.impl;

import com.ds.goroute.entity.Place;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.PlaceSearchIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlaceUsageStatsServiceImplTest {

    private final PlaceRepository placeRepository = mock(PlaceRepository.class);
    private final PlaceSearchIndexService index = mock(PlaceSearchIndexService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final PlaceUsageStatsServiceImpl service;

    PlaceUsageStatsServiceImplTest() {
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        service = new PlaceUsageStatsServiceImpl(placeRepository, index, transactions);
    }

    @Test
    void nothingMarkedRecountsNothing() {
        assertThat(service.recountMarked()).isZero();

        verify(placeRepository, never()).recomputeUsageCounts(any());
    }

    @Test
    void markedReferencesAreResolvedRecountedOnceAndReindexedWhenChanged() {
        UUID catalogueId = UUID.randomUUID();
        UUID fromGoogleId = UUID.randomUUID();
        when(placeRepository.findIdsByReferences(anyList(), anyList()))
                .thenReturn(List.of(catalogueId, fromGoogleId));
        when(placeRepository.recomputeUsageCounts(List.of(catalogueId, fromGoogleId)))
                .thenReturn(List.of(catalogueId));
        Place row = Place.builder().id(catalogueId).build();
        when(placeRepository.findSearchIndexRow(catalogueId)).thenReturn(Optional.of(row));

        service.markChanged(List.of(catalogueId.toString(), "ChIJ-google"));
        service.markChanged(List.of(catalogueId.toString()));

        assertThat(service.recountMarked()).isEqualTo(1);
        verify(placeRepository, times(1)).recomputeUsageCounts(anyList());
        verify(index).indexPlace(row);
        // Drained: a second run has nothing to do.
        assertThat(service.recountMarked()).isZero();
    }

    @Test
    void aFailedRecountKeepsItsMarksForTheNextRun() {
        UUID placeId = UUID.randomUUID();
        when(placeRepository.findIdsByReferences(anyList(), anyList())).thenReturn(List.of(placeId));
        when(placeRepository.recomputeUsageCounts(anyList()))
                .thenThrow(new IllegalStateException("database down"))
                .thenReturn(List.of());

        service.markChanged(List.of(placeId.toString()));

        assertThatThrownBy(service::recountMarked).isInstanceOf(IllegalStateException.class);
        service.recountMarked();
        verify(placeRepository, times(2)).recomputeUsageCounts(List.of(placeId));
    }

    @Test
    void blankReferencesAreIgnored() {
        service.markChanged(java.util.Arrays.asList(" ", null));

        assertThat(service.recountMarked()).isZero();
        verify(placeRepository, never()).findIdsByReferences(any(), any());
    }

    @Test
    void theNightlyRunRecountsEveryPlace() {
        when(placeRepository.recomputeUsageCounts(null)).thenReturn(List.of());

        assertThat(service.recountAll()).isZero();

        verify(placeRepository).recomputeUsageCounts(null);
    }
}
