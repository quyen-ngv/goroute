package com.ds.goroute.service.impl;

import com.ds.goroute.dto.ActivityPackageUnit;
import com.ds.goroute.dto.response.ActivityAvailabilityDayResponse;
import com.ds.goroute.entity.ActivityPackage;
import com.ds.goroute.entity.ActivitySlot;
import com.ds.goroute.entity.MarketplaceActivityProduct;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.service.marketplace.MarketplaceDisplayPrice;
import com.ds.goroute.type.ActivityProductType;
import com.ds.goroute.type.ActivityUnitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ActivityDetailContentServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ActivityCommerceRepository repository = mock(ActivityCommerceRepository.class);
    private final MarketplaceDisplayPrice displayPrice = mock(MarketplaceDisplayPrice.class);
    private final ActivityCommerceServiceImpl service = new ActivityCommerceServiceImpl(
            repository, null, null, null, null, null, objectMapper, null, null, displayPrice);

    private final UUID activityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(displayPrice.convert(any(BigDecimal.class), any())).thenAnswer(inv -> inv.getArgument(0));
        when(displayPrice.currencyOf(any())).thenAnswer(inv -> inv.getArgument(0));
        when(repository.findPublicProduct(activityId)).thenReturn(Optional.of(MarketplaceActivityProduct.builder()
                .id(activityId).activityType("ATTRACTION").title("Water puppet show").priceCurrency("VND")
                .searchLat(21.03).searchLng(105.85).build()));
    }

    @Test
    void ticketTypesAndTourTypesSplitTheCatalogue() {
        assertThat(ActivityProductType.kindOf("ATTRACTION")).isEqualTo("TICKET");
        assertThat(ActivityProductType.kindOf("EVENT")).isEqualTo("TICKET");
        assertThat(ActivityProductType.kindOf("PASS")).isEqualTo("TICKET");
        assertThat(ActivityProductType.kindOf("TOUR")).isEqualTo("TOUR");
        assertThat(ActivityProductType.kindOf("TRANSFER")).isEqualTo("TOUR");
        assertThat(ActivityProductType.kindOf("UNKNOWN")).isEqualTo("TOUR");
    }

    @Test
    void listFilteredByKindOnlyAsksForThatKindsTypes() {
        service.listPublic(null, "ticket", 0, 20);

        verify(repository).findProductsPublic(isNull(), eq(List.of("ATTRACTION", "EVENT", "PASS")), anyInt(), anyInt());
    }

    @Test
    void unknownKindIsRejected() {
        assertThatThrownBy(() -> service.listPublic(null, "hotel", 0, 20)).isInstanceOf(BusinessException.class);
    }

    @Test
    void similarProductsStayInTheSameKind() {
        service.listSimilarPublic(activityId, 6);

        verify(repository).findSimilarProductsPublic(eq(activityId), eq(List.of("ATTRACTION", "EVENT", "PASS")), eq(21.03), eq(105.85), eq(6));
    }

    @Test
    void availabilityGroupsOpenSlotsByDateWithCheapestPaidUnitPrice() throws Exception {
        LocalDate day = LocalDate.now().plusDays(3);
        UUID standardId = UUID.randomUUID();
        List<ActivityPackageUnit> units = List.of(
                ActivityPackageUnit.builder().code("ADULT").name("Adult").unitType(ActivityUnitType.ADULT).price(BigDecimal.valueOf(170_000)).build(),
                ActivityPackageUnit.builder().code("INFANT").name("Infant").unitType(ActivityUnitType.INFANT).price(BigDecimal.ZERO).build());
        ActivityPackage standard = ActivityPackage.builder().id(standardId).activityBookingId(activityId).currency("VND")
                .basePrice(BigDecimal.valueOf(170_000)).originalPrice(BigDecimal.valueOf(220_000))
                .units(objectMapper.writeValueAsString(units)).build();
        when(repository.findPackages(activityId, false)).thenReturn(List.of(standard));
        when(repository.findSlots(eq(standardId), any(), anyBoolean())).thenReturn(List.of(
                slot(day.atTime(LocalTime.of(15, 0)), 50, "{\"ADULT\": 150000}"),
                slot(day.atTime(LocalTime.of(17, 0)), 20, "{}"),
                slot(day.plusDays(1).atTime(LocalTime.of(15, 0)), 0, "{}")));

        List<ActivityAvailabilityDayResponse> days = service.availabilityPublic(activityId, LocalDate.now(), 30);

        assertThat(days).hasSize(1);
        ActivityAvailabilityDayResponse first = days.get(0);
        assertThat(first.getDate()).isEqualTo(day);
        assertThat(first.getFromPrice()).isEqualByComparingTo("150000");
        assertThat(first.getPackages()).singleElement().satisfies(p -> {
            assertThat(p.getPackageId()).isEqualTo(standardId);
            assertThat(p.getSlotCount()).isEqualTo(2);
            assertThat(p.getAvailableQuantity()).isEqualTo(70);
            assertThat(p.getOriginalPrice()).isEqualByComparingTo("220000");
        });
    }

    private ActivitySlot slot(LocalDateTime startsAt, int available, String unitPrices) {
        return ActivitySlot.builder().id(UUID.randomUUID()).startsAt(startsAt).timezone("Asia/Ho_Chi_Minh")
                .availableQuantity(available).bookingCutoffMinutes(0).status("ENABLED").unitPrices(unitPrices).build();
    }
}
