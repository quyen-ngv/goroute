package com.ds.goroute.service.impl;

import com.ds.goroute.entity.MarketplaceEntityVersion;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.MarketplaceHistoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplaceHistoryServiceImplTest {
    @Test
    void retriesAtomicInsertInsteadOfTakingAnAdvisoryLock() {
        MarketplaceHistoryRepository repository = mock(MarketplaceHistoryRepository.class);
        when(repository.tryInsertNextVersion(any())).thenReturn(false, true);
        MarketplaceHistoryServiceImpl service = new MarketplaceHistoryServiceImpl(
                repository, new ObjectMapper(), mock(AdminMapper.class));

        service.record(UUID.randomUUID(), "HOTEL", UUID.randomUUID(), "UPDATED", Map.of("name", "A"),
                List.of("name"), UUID.randomUUID(), "USER", null);

        ArgumentCaptor<MarketplaceEntityVersion> versions = ArgumentCaptor.forClass(MarketplaceEntityVersion.class);
        verify(repository, times(2)).tryInsertNextVersion(versions.capture());
        verify(repository).insertAuditEvent(any());
        assertEquals("UPDATED", versions.getAllValues().get(0).getAction());
        assertEquals("UPDATED", versions.getAllValues().get(1).getAction());
    }
}
