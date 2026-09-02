package com.ds.goroute.service.impl;

import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.HotelBooking;
import com.ds.goroute.entity.HotelBookingItem;
import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.MarketplaceActivityProduct;
import com.ds.goroute.entity.ActivityOrderItem;
import com.ds.goroute.entity.ActivityPackage;
import com.ds.goroute.entity.ActivitySlot;
import com.ds.goroute.entity.RatePlan;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.dto.request.CreateHotelBookingRequest;
import com.ds.goroute.dto.request.CreateActivityOrderRequest;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;

class MarketplacePublicAccessServiceImplTest {
    @Test
    void hotelDetailUsesTheSamePublicVisibilityFilterAsSearch() {
        HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
        UUID hotelId = UUID.randomUUID();
        HotelProfile hotel = HotelProfile.builder().id(hotelId).status("ENABLED").build();
        when(hotels.findPublicHotel(hotelId)).thenReturn(Optional.of(hotel));

        HotelMarketplaceServiceImpl service = new HotelMarketplaceServiceImpl(
                hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class), mock(NotificationService.class),
                new ObjectMapper().findAndRegisterModules(), mock(AdminMapper.class), mock(com.ds.goroute.repository.MarketplacePromotionRepository.class), mock(com.ds.goroute.service.MarketplaceCommissionService.class));

        assertEquals(hotelId, service.getPublic(hotelId).getId());
        verify(hotels).findPublicHotel(hotelId);
        verify(hotels, never()).findHotel(hotelId);
    }

    @Test
    void activityDetailUsesTheSamePublicVisibilityFilterAsSearch() {
        ActivityCommerceRepository activities = mock(ActivityCommerceRepository.class);
        UUID activityId = UUID.randomUUID();
        MarketplaceActivityProduct activity = MarketplaceActivityProduct.builder()
                .id(activityId).productStatus("ENABLED").inventoryMode("INTERNAL").build();
        when(activities.findPublicProduct(activityId)).thenReturn(Optional.of(activity));

        ActivityCommerceServiceImpl service = new ActivityCommerceServiceImpl(
                activities, mock(PlaceRepository.class), mock(HostOrganizationRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class),
                mock(com.ds.goroute.service.MarketplaceCommissionService.class), new ObjectMapper().findAndRegisterModules(), mock(NotificationService.class), mock(AdminMapper.class));

        assertEquals(activityId, service.getPublic(activityId).getId());
        verify(activities).findPublicProduct(activityId);
        verify(activities, never()).findProduct(activityId);
    }

    @Test
    void hotelBookingRejectsAClosedDepartureBeforeReservingInventory() {
        HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
        UUID hotelId = UUID.randomUUID();
        UUID roomId = UUID.randomUUID();
        UUID rateId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.now().plusDays(1);
        LocalDate checkOut = checkIn.plusDays(2);
        when(hotels.findPublicHotel(hotelId)).thenReturn(Optional.of(
                HotelProfile.builder().id(hotelId).organizationId(UUID.randomUUID()).build()));
        when(hotels.findRoomType(roomId)).thenReturn(Optional.of(RoomType.builder()
                .id(roomId).hotelId(hotelId).status("ENABLED")
                .maxAdults(2).maxChildren(0).maxOccupancy(2).build()));
        when(hotels.findRatePlan(rateId)).thenReturn(Optional.of(RatePlan.builder()
                .id(rateId).roomTypeId(roomId).status("ENABLED")
                .currency("VND").basePrice(java.math.BigDecimal.ONE).minStay(1).build()));
        when(hotels.findAvailability(org.mockito.ArgumentMatchers.eq(hotelId), org.mockito.ArgumentMatchers.eq(roomId), org.mockito.ArgumentMatchers.eq(rateId), org.mockito.ArgumentMatchers.eq(checkIn), org.mockito.ArgumentMatchers.eq(checkOut), org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                HotelAvailabilityDay.builder().inventoryDate(checkIn).availableUnits(1)
                        .stopSell(false).closedToArrival(false).closedToDeparture(false)
                        .minStay(1).nightlyPrice(java.math.BigDecimal.ONE).build(),
                HotelAvailabilityDay.builder().inventoryDate(checkIn.plusDays(1)).availableUnits(1)
                        .stopSell(false).closedToArrival(false).closedToDeparture(true)
                        .minStay(1).nightlyPrice(java.math.BigDecimal.ONE).build()));
        HotelMarketplaceServiceImpl service = new HotelMarketplaceServiceImpl(
                hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class), mock(NotificationService.class),
                new ObjectMapper(), mock(AdminMapper.class), mock(com.ds.goroute.repository.MarketplacePromotionRepository.class), mock(com.ds.goroute.service.MarketplaceCommissionService.class));
        CreateHotelBookingRequest request = new CreateHotelBookingRequest();
        request.setHotelId(hotelId);
        request.setRoomTypeId(roomId);
        request.setRatePlanId(rateId);
        request.setCheckInDate(checkIn);
        request.setCheckOutDate(checkOut);
        request.setQuantity(1);
        request.setAdults(1);
        request.setChildren(0);
        request.setGuestLead(Map.of("fullName", "Traveler"));

        assertThrows(BusinessException.class, () -> service.createBooking(UUID.randomUUID(), request));
        verify(hotels, never()).reserveInventory(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void activityOrderPricesConfiguredUnitsAndReservesTheirPaxCount() throws Exception {
        ActivityCommerceRepository activities = mock(ActivityCommerceRepository.class);
        UUID activityId = UUID.randomUUID(); UUID packageId = UUID.randomUUID(); UUID slotId = UUID.randomUUID();
        MarketplaceActivityProduct product = MarketplaceActivityProduct.builder().id(activityId).organizationId(UUID.randomUUID()).productStatus("ENABLED").build();
        ActivityPackage pack = ActivityPackage.builder().id(packageId).activityBookingId(activityId).status("ENABLED").currency("VND")
                .basePrice(BigDecimal.valueOf(100)).minQuantity(1).maxQuantity(10)
                .units("[{\"code\":\"ADULT\",\"name\":\"Adult\",\"unitType\":\"ADULT\",\"price\":100,\"paxCount\":1},{\"code\":\"CHILD\",\"name\":\"Child\",\"unitType\":\"CHILD\",\"price\":50,\"paxCount\":1}]").build();
        ActivitySlot slot = ActivitySlot.builder().id(slotId).packageId(packageId).startsAt(LocalDateTime.now().plusDays(1)).bookingCutoffMinutes(60)
                .status("ENABLED").unitPrices("{\"ADULT\":120}").build();
        when(activities.findPublicProduct(activityId)).thenReturn(Optional.of(product));
        when(activities.findPackage(packageId)).thenReturn(Optional.of(pack));
        when(activities.findSlot(slotId)).thenReturn(Optional.of(slot));
        when(activities.reserveSlot(org.mockito.ArgumentMatchers.eq(slotId), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(activities.confirmSlot(org.mockito.ArgumentMatchers.eq(slotId), org.mockito.ArgumentMatchers.eq(3), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(activities.findOrderItem(org.mockito.ArgumentMatchers.any())).thenReturn(Optional.empty());
        ActivityCommerceServiceImpl service = new ActivityCommerceServiceImpl(
                activities, mock(PlaceRepository.class), mock(HostOrganizationRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class),
                mock(com.ds.goroute.service.MarketplaceCommissionService.class), new ObjectMapper().findAndRegisterModules(), mock(NotificationService.class), mock(AdminMapper.class));
        CreateActivityOrderRequest request = new CreateActivityOrderRequest();request.setActivityId(activityId);request.setPackageId(packageId);request.setSlotId(slotId);request.setUnitQuantities(Map.of("adult",2,"CHILD",1));

        service.createOrder(UUID.randomUUID(),request);

        ArgumentCaptor<ActivityOrderItem> item = ArgumentCaptor.forClass(ActivityOrderItem.class);verify(activities).insertOrderItem(item.capture());
        assertEquals(3,item.getValue().getQuantity());assertEquals(new BigDecimal("290"),item.getValue().getTotalPrice());
    }

    @Test
    void hotelBookingIdempotencyReturnsTheExistingHoldWithoutReservingAgain() {
        HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
        UUID userId = UUID.randomUUID(); UUID bookingId = UUID.randomUUID();
        HotelBooking existing = HotelBooking.builder().id(bookingId).userId(userId).bookingCode("HTL-EXISTING").build();
        when(hotels.findBookingByUserAndIdempotencyKey(userId, "retry-safe-key")).thenReturn(Optional.of(existing));
        when(hotels.findBookingItems(bookingId)).thenReturn(List.of());
        HotelMarketplaceServiceImpl service = new HotelMarketplaceServiceImpl(hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class), mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class), mock(NotificationService.class), new ObjectMapper().findAndRegisterModules(), mock(AdminMapper.class), mock(com.ds.goroute.repository.MarketplacePromotionRepository.class), mock(com.ds.goroute.service.MarketplaceCommissionService.class));
        CreateHotelBookingRequest request = new CreateHotelBookingRequest();request.setIdempotencyKey("retry-safe-key");

        assertEquals(bookingId, service.createBooking(userId, request).getId());
        verify(hotels, never()).findPublicHotel(org.mockito.ArgumentMatchers.any());
        verify(hotels, never()).reserveInventory(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyInt(),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any());
    }

    @Test
    void expiredHotelPaymentHoldReleasesReservedInventoryAfterCompareAndSet() {
        HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
        UUID bookingId = UUID.randomUUID(); UUID roomId = UUID.randomUUID(); LocalDate checkIn = LocalDate.now().plusDays(2); LocalDate checkOut = checkIn.plusDays(2);
        HotelBooking booking = HotelBooking.builder().id(bookingId).userId(UUID.randomUUID()).bookingCode("HTL-HOLD").organizationId(UUID.randomUUID()).checkInDate(checkIn).checkOutDate(checkOut).bookingStatus("PENDING_PARTNER_CONFIRMATION").dataVersion(4L).build();
        HotelBookingItem item = HotelBookingItem.builder().bookingId(bookingId).roomTypeId(roomId).quantity(2).build();
        when(hotels.findExpiredPendingBookings(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(booking));
        when(hotels.findBooking(bookingId)).thenReturn(Optional.of(booking));
        when(hotels.expireBookingHold(org.mockito.ArgumentMatchers.eq(bookingId), org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(1);
        when(hotels.findBookingItems(bookingId)).thenReturn(List.of(item));
        when(hotels.releaseInventory(org.mockito.ArgumentMatchers.eq(roomId), org.mockito.ArgumentMatchers.eq(checkIn), org.mockito.ArgumentMatchers.eq(checkOut), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any())).thenReturn(2);
        HotelMarketplaceServiceImpl service = new HotelMarketplaceServiceImpl(hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class), mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class), mock(NotificationService.class), new ObjectMapper().findAndRegisterModules(), mock(AdminMapper.class), mock(com.ds.goroute.repository.MarketplacePromotionRepository.class), mock(com.ds.goroute.service.MarketplaceCommissionService.class));

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        assertEquals(List.of(bookingId), service.findExpiredPendingBookingIds(now, 100));
        assertTrue(service.expirePendingBooking(bookingId, now));
        verify(hotels).releaseInventory(org.mockito.ArgumentMatchers.eq(roomId), org.mockito.ArgumentMatchers.eq(checkIn), org.mockito.ArgumentMatchers.eq(checkOut), org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.eq(true), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.any());
    }

    /** Builds a service whose repositories answer for one hotel/room/rate and one two-night stay. */
    private HotelMarketplaceServiceImpl pricingService(HotelMarketplaceRepository hotels,
                                                       com.ds.goroute.repository.MarketplacePromotionRepository promotions,
                                                       com.ds.goroute.service.MarketplaceCommissionService commissions) {
        return new HotelMarketplaceServiceImpl(hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class), mock(NotificationService.class),
                new ObjectMapper(), mock(AdminMapper.class), promotions, commissions);
    }

    private HotelMarketplaceRepository pricingRepository(UUID hotelId, UUID roomId, UUID rateId, RatePlan rate,
                                                         LocalDate checkIn, BigDecimal nightlyPrice) {
        HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
        when(hotels.findHotel(hotelId)).thenReturn(Optional.of(HotelProfile.builder().id(hotelId)
                .organizationId(UUID.randomUUID()).timezone("Asia/Ho_Chi_Minh").build()));
        when(hotels.findRoomType(roomId)).thenReturn(Optional.of(RoomType.builder().id(roomId).hotelId(hotelId)
                .status("ENABLED").maxAdults(4).maxChildren(2).maxOccupancy(4).build()));
        when(hotels.findRatePlan(rateId)).thenReturn(Optional.of(rate));
        when(hotels.findAvailability(org.mockito.ArgumentMatchers.eq(hotelId), org.mockito.ArgumentMatchers.eq(roomId),
                org.mockito.ArgumentMatchers.eq(rateId), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                HotelAvailabilityDay.builder().inventoryDate(checkIn).availableUnits(3).stopSell(false)
                        .closedToArrival(false).closedToDeparture(false).minStay(1).nightlyPrice(nightlyPrice).build(),
                HotelAvailabilityDay.builder().inventoryDate(checkIn.plusDays(1)).availableUnits(3).stopSell(false)
                        .closedToArrival(false).closedToDeparture(false).minStay(1).nightlyPrice(nightlyPrice).build()));
        return hotels;
    }

    @Test
    void occupancyTableReplacesTheNightlyPriceAndSuppressesTheExtraAdultFee() {
        UUID hotelId = UUID.randomUUID(); UUID roomId = UUID.randomUUID(); UUID rateId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.now().plusDays(10);
        RatePlan rate = RatePlan.builder().id(rateId).roomTypeId(roomId).status("ENABLED").currency("VND")
                .basePrice(new BigDecimal("800000")).pricingModel("OCCUPANCY_BASED").baseOccupancy(2)
                .extraAdultFee(new BigDecimal("300000")).occupancyPricing("{\"2\":900000,\"3\":1100000}").minStay(1).build();
        HotelMarketplaceRepository hotels = pricingRepository(hotelId, roomId, rateId, rate, checkIn, new BigDecimal("800000"));
        com.ds.goroute.repository.MarketplacePromotionRepository promotions = mock(com.ds.goroute.repository.MarketplacePromotionRepository.class);
        HotelMarketplaceServiceImpl service = pricingService(hotels, promotions, mock(com.ds.goroute.service.MarketplaceCommissionService.class));

        var days = service.getAvailability(hotelId, roomId, rateId, checkIn, checkIn.plusDays(2), 1, 3, 0);

        // 3 adults in one room -> the table's price for 3, and the extra-adult fee is not charged on top
        assertEquals(0, new BigDecimal("1100000.00").compareTo(days.get(0).getQuotedNightlyPrice()));
        assertEquals(0, new BigDecimal("2200000.00").compareTo(days.get(0).getQuotedTotal()));
    }

    @Test
    void lengthOfStayTableDiscountsTheWholeStay() {
        UUID hotelId = UUID.randomUUID(); UUID roomId = UUID.randomUUID(); UUID rateId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.now().plusDays(10);
        RatePlan rate = RatePlan.builder().id(rateId).roomTypeId(roomId).status("ENABLED").currency("VND")
                .basePrice(new BigDecimal("1000000")).pricingModel("LENGTH_OF_STAY").baseOccupancy(2)
                .occupancyPricing("{\"2\":10,\"5\":20}").minStay(1).build();
        HotelMarketplaceRepository hotels = pricingRepository(hotelId, roomId, rateId, rate, checkIn, new BigDecimal("1000000"));
        HotelMarketplaceServiceImpl service = pricingService(hotels, mock(com.ds.goroute.repository.MarketplacePromotionRepository.class), mock(com.ds.goroute.service.MarketplaceCommissionService.class));

        var days = service.getAvailability(hotelId, roomId, rateId, checkIn, checkIn.plusDays(2), 1, 2, 0);

        // two nights reaches the "2 nights -> 10% off" tier, not the 5-night one
        assertEquals(0, new BigDecimal("900000.00").compareTo(days.get(0).getQuotedNightlyPrice()));
    }

    @Test
    void anEnabledPromotionDiscountsTheQuotedNightAndKeepsTheOriginal() {
        UUID hotelId = UUID.randomUUID(); UUID roomId = UUID.randomUUID(); UUID rateId = UUID.randomUUID();
        LocalDate checkIn = LocalDate.now().plusDays(40);
        RatePlan rate = RatePlan.builder().id(rateId).roomTypeId(roomId).status("ENABLED").currency("VND")
                .basePrice(new BigDecimal("1000000")).pricingModel("STANDARD").baseOccupancy(2).minStay(1).build();
        HotelMarketplaceRepository hotels = pricingRepository(hotelId, roomId, rateId, rate, checkIn, new BigDecimal("1000000"));
        com.ds.goroute.repository.MarketplacePromotionRepository promotions = mock(com.ds.goroute.repository.MarketplacePromotionRepository.class);
        when(promotions.findApplicable(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(com.ds.goroute.entity.RatePlanPromotion.builder().id(UUID.randomUUID())
                        .hotelId(hotelId).ratePlanId(null).code("EARLY30").name("Early bird").promotionType("EARLY_BIRD")
                        .discountPercent(new BigDecimal("20")).priority(100).minAdvanceDays(30).daysOfWeek("[]")
                        .status("ENABLED").createdAt(LocalDateTime.now().minusDays(5)).build()));
        HotelMarketplaceServiceImpl service = pricingService(hotels, promotions, mock(com.ds.goroute.service.MarketplaceCommissionService.class));

        var days = service.getAvailability(hotelId, roomId, rateId, checkIn, checkIn.plusDays(2), 1, 2, 0);

        assertEquals(0, new BigDecimal("800000.00").compareTo(days.get(0).getQuotedNightlyPrice()));
        assertEquals(0, new BigDecimal("1000000.00").compareTo(days.get(0).getOriginalNightlyPrice()));
        assertEquals("EARLY30", days.get(0).getPromotionCode());
        assertEquals(0, new BigDecimal("1600000.00").compareTo(days.get(0).getQuotedTotal()));
    }
}
