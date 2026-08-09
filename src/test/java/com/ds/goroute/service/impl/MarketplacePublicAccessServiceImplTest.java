package com.ds.goroute.service.impl;

import com.ds.goroute.entity.HotelProfile;
import com.ds.goroute.entity.HotelAvailabilityDay;
import com.ds.goroute.entity.MarketplaceActivityProduct;
import com.ds.goroute.entity.RatePlan;
import com.ds.goroute.entity.RoomType;
import com.ds.goroute.dto.request.CreateHotelBookingRequest;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketplacePublicAccessServiceImplTest {
    @Test
    void hotelDetailUsesTheSamePublicVisibilityFilterAsSearch() {
        HotelMarketplaceRepository hotels = mock(HotelMarketplaceRepository.class);
        UUID hotelId = UUID.randomUUID();
        HotelProfile hotel = HotelProfile.builder().id(hotelId).status("ENABLED").build();
        when(hotels.findPublicHotel(hotelId)).thenReturn(Optional.of(hotel));

        HotelMarketplaceServiceImpl service = new HotelMarketplaceServiceImpl(
                hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class),
                new ObjectMapper(), mock(AdminMapper.class));

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
                new ObjectMapper(), mock(AdminMapper.class));

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
        when(hotels.findAvailability(hotelId, roomId, rateId, checkIn, checkOut)).thenReturn(List.of(
                HotelAvailabilityDay.builder().inventoryDate(checkIn).availableUnits(1)
                        .stopSell(false).closedToArrival(false).closedToDeparture(false)
                        .minStay(1).nightlyPrice(java.math.BigDecimal.ONE).build(),
                HotelAvailabilityDay.builder().inventoryDate(checkIn.plusDays(1)).availableUnits(1)
                        .stopSell(false).closedToArrival(false).closedToDeparture(true)
                        .minStay(1).nightlyPrice(java.math.BigDecimal.ONE).build()));
        HotelMarketplaceServiceImpl service = new HotelMarketplaceServiceImpl(
                hotels, mock(HostOrganizationRepository.class), mock(PlaceRepository.class),
                mock(PartnerAuthorizationService.class), mock(MarketplaceHistoryService.class),
                new ObjectMapper(), mock(AdminMapper.class));
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
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
