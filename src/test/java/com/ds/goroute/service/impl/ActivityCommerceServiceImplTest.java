package com.ds.goroute.service.impl;

import com.ds.goroute.dto.ActivityPackageUnit;
import com.ds.goroute.dto.request.CreateActivityOrderRequest;
import com.ds.goroute.dto.request.BulkCreateActivitySlotsRequest;
import com.ds.goroute.dto.request.UpsertActivitySlotRequest;
import com.ds.goroute.entity.ActivityPackage;
import com.ds.goroute.entity.ActivitySlot;
import com.ds.goroute.entity.MarketplaceActivityProduct;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.type.ActivityUnitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityCommerceServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ActivityCommerceServiceImpl service = new ActivityCommerceServiceImpl(
            null, null, null, null, null, null, objectMapper, null, null, null);

    @Test
    void packageSaleQuantityIsIndependentFromConsumedCapacity() throws Exception {
        ActivityPackageUnit group = ActivityPackageUnit.builder()
                .code("GROUP")
                .name("Nhóm 3 khách")
                .unitType(ActivityUnitType.GROUP)
                .price(BigDecimal.valueOf(900_000))
                .paxCount(3)
                .build();
        ActivityPackage activityPackage = ActivityPackage.builder()
                .basePrice(BigDecimal.valueOf(900_000))
                .units(objectMapper.writeValueAsString(List.of(group)))
                .build();
        ActivitySlot slot = ActivitySlot.builder().unitPrices("{}").build();
        CreateActivityOrderRequest request = new CreateActivityOrderRequest();
        request.setUnitQuantities(Map.of("GROUP", 2));

        ActivityCommerceServiceImpl.OrderPricing pricing = service.orderPricing(activityPackage, slot, request);

        assertEquals(2, pricing.saleQuantity());
        assertEquals(6, pricing.capacityQuantity());
        assertEquals(0, BigDecimal.valueOf(1_800_000).compareTo(pricing.total()));
    }

    @Test
    void bulkSlotCreationPersistsEverySlotInOneServiceCall() {
        ActivityCommerceRepository repository = mock(ActivityCommerceRepository.class);
        PartnerAuthorizationService authorization = mock(PartnerAuthorizationService.class);
        MarketplaceHistoryService history = mock(MarketplaceHistoryService.class);
        ActivityCommerceServiceImpl bulkService = new ActivityCommerceServiceImpl(repository, null, null, authorization, history, null, objectMapper, null, null, null);
        UUID actorId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID activityId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        ActivityPackage activityPackage = ActivityPackage.builder().id(packageId).activityBookingId(activityId).units("[]").build();
        MarketplaceActivityProduct product = MarketplaceActivityProduct.builder().id(activityId).organizationId(organizationId).build();
        when(repository.findPackage(packageId)).thenReturn(Optional.of(activityPackage));
        when(repository.findProduct(activityId)).thenReturn(Optional.of(product));
        when(repository.findSlots(any(), any(), any(Boolean.class))).thenReturn(List.of());
        when(repository.findSlot(any())).thenReturn(Optional.empty());
        BulkCreateActivitySlotsRequest request = new BulkCreateActivitySlotsRequest();
        request.setSlots(List.of(slotRequest(LocalDateTime.now().plusDays(1)), slotRequest(LocalDateTime.now().plusDays(2))));

        assertEquals(2, bulkService.partnerCreateSlots(actorId, packageId, request).size());
        verify(repository, times(2)).insertSlot(any(ActivitySlot.class));
    }

    private UpsertActivitySlotRequest slotRequest(LocalDateTime startsAt) {
        UpsertActivitySlotRequest request = new UpsertActivitySlotRequest();
        request.setStartsAt(startsAt);
        request.setEndsAt(startsAt.plusHours(2));
        request.setTimezone("Asia/Ho_Chi_Minh");
        request.setCapacity(10);
        request.setBlockedQuantity(0);
        request.setBookingCutoffMinutes(60);
        return request;
    }
}
