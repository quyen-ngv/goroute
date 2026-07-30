package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HotelMarketplaceServiceImpl implements HotelMarketplaceService {
    private static final int MAX_BOOKING_NIGHTS = 90;
    private static final int DEFAULT_INVENTORY_DAYS = 730;
    private static final Set<String> CANCELLATION_STATUSES = Set.of(
            "CANCELLED_BY_GUEST", "CANCELLED_BY_HOST", "CANCELLED_BY_PLATFORM", "EXPIRED", "FAILED");

    private final HotelMarketplaceRepository repository;
    private final HostOrganizationRepository organizationRepository;
    private final PlaceRepository placeRepository;
    private final PartnerAuthorizationService authorizationService;
    private final MarketplaceHistoryService historyService;
    private final ObjectMapper objectMapper;
    private final AdminMapper adminMapper;

    @Override public List<HotelProfileResponse> listPublic(String query, int page, int size) {
        PageRange range = pageRange(page, size);
        return repository.findHotelsPublic(blankToNull(query), range.limit(), range.offset()).stream().map(this::hotelResponse).toList();
    }

    @Override public HotelProfileResponse getPublic(UUID hotelId) {
        HotelProfile hotel = hotelRequired(hotelId);
        if (!"ENABLED".equals(hotel.getStatus()) || !organizationBookable(hotel.getOrganizationId())) {
            throw notFound("Hotel not found");
        }
        return hotelResponse(hotel);
    }

    @Override public List<RoomTypeResponse> listPublicRooms(UUID hotelId) {
        getPublic(hotelId);
        return repository.findRoomTypes(hotelId, false).stream().map(this::roomResponse).toList();
    }

    @Override public List<RatePlanResponse> listPublicRates(UUID roomTypeId) {
        RoomType room = roomRequired(roomTypeId);
        getPublic(room.getHotelId());
        if (!"ENABLED".equals(room.getStatus())) throw notFound("Room type not found");
        return repository.findRatePlans(roomTypeId, false).stream().map(this::rateResponse).toList();
    }

    @Override public List<RoomInventoryResponse> getAvailability(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                                                 LocalDate checkIn, LocalDate checkOut) {
        validateStay(checkIn, checkOut);
        List<HotelAvailabilityDay> days = repository.findAvailability(hotelId, roomTypeId, ratePlanId, checkIn, checkOut);
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        if (days.size() != nights) return List.of();
        return days.stream().map(day -> RoomInventoryResponse.builder().roomTypeId(roomTypeId)
                .inventoryDate(day.getInventoryDate()).availableUnits(day.getAvailableUnits())
                .stopSell(day.getStopSell()).priceOverride(day.getNightlyPrice()).minStay(day.getMinStay())
                .closedToArrival(day.getClosedToArrival()).closedToDeparture(day.getClosedToDeparture()).build()).toList();
    }

    @Override public List<HotelProfileResponse> partnerListHotels(UUID actor, UUID organizationId) {
        authorizationService.requireOrganization(organizationId, actor);
        return repository.findHotelsByOrganization(organizationId).stream()
                .filter(hotel->authorizationService.hasResourcePermission(organizationId,actor,"HOTEL",hotel.getId(),"HOTEL_READ"))
                .map(this::hotelResponse).toList();
    }

    @Override
    @Transactional
    public HotelProfileResponse partnerCreateHotel(UUID actor, UpsertHotelRequest request) {
        HostOrganization org = authorizationService.requirePermission(request.getOrganizationId(), actor, "HOTEL_WRITE");
        placeRepository.findById(request.getPlaceId()).orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        String timezone = request.getTimezone() == null ? org.getTimezone() : request.getTimezone();
        validateTimezone(timezone);
        LocalDateTime now = LocalDateTime.now();
        HotelProfile hotel = HotelProfile.builder().id(UUID.randomUUID()).organizationId(org.getId()).placeId(request.getPlaceId())
                .propertyCode(blankToNull(request.getPropertyCode())).propertyType(defaulted(request.getPropertyType(), "HOTEL"))
                .starRating(request.getStarRating()).description(blankToNull(request.getDescription()))
                .checkInTime(request.getCheckInTime()).checkOutTime(request.getCheckOutTime()).timezone(timezone)
                .amenities(json(defaultList(request.getAmenities()))).policies(json(defaultMap(request.getPolicies())))
                .bookingContact(json(defaultMap(request.getBookingContact()))).status(defaulted(request.getStatus(), "DRAFT"))
                .disabledReason(blankToNull(request.getDisabledReason())).dataVersion(1L).createdBy(actor).updatedBy(actor)
                .createdAt(now).updatedAt(now).build();
        try { repository.insertHotel(hotel); } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This place already has a hotel profile or property code is duplicated");
        }
        historyService.record(org.getId(), "HOTEL", hotel.getId(), "CREATED", hotel, List.of(), actor, actorType(actor), null);
        return hotelResponse(repository.findHotel(hotel.getId()).orElse(hotel));
    }

    @Override
    @Transactional
    public HotelProfileResponse partnerUpdateHotel(UUID actor, UUID hotelId, UpsertHotelRequest request) {
        HotelProfile hotel = hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor, "HOTEL",hotelId,"HOTEL_WRITE");
        if ("SUSPENDED".equals(hotel.getStatus())) {
            throw forbidden("A suspended hotel can only be changed by an admin");
        }
        if (!hotel.getOrganizationId().equals(request.getOrganizationId()) || !hotel.getPlaceId().equals(request.getPlaceId())) {
            throw badRequest("organizationId and placeId are immutable");
        }
        String timezone = defaulted(request.getTimezone(), hotel.getTimezone()); validateTimezone(timezone);
        long expected = expected(request.getExpectedVersion(), hotel.getDataVersion());
        hotel.setPropertyCode(blankToNull(request.getPropertyCode())); hotel.setPropertyType(defaulted(request.getPropertyType(), hotel.getPropertyType()));
        hotel.setStarRating(request.getStarRating()); hotel.setDescription(blankToNull(request.getDescription()));
        hotel.setCheckInTime(request.getCheckInTime()); hotel.setCheckOutTime(request.getCheckOutTime()); hotel.setTimezone(timezone);
        hotel.setAmenities(json(defaultList(request.getAmenities()))); hotel.setPolicies(json(defaultMap(request.getPolicies())));
        hotel.setBookingContact(json(defaultMap(request.getBookingContact()))); hotel.setStatus(defaulted(request.getStatus(), hotel.getStatus()));
        hotel.setDisabledReason(blankToNull(request.getDisabledReason())); hotel.setDataVersion(expected); hotel.setUpdatedBy(actor);
        hotel.setUpdatedAt(LocalDateTime.now());
        optimistic(repository.updateHotel(hotel), "Hotel"); hotel.setDataVersion(expected + 1);
        historyService.record(hotel.getOrganizationId(), "HOTEL", hotelId, "UPDATED", hotel, List.of("PROFILE"), actor, actorType(actor), null);
        return hotelResponse(repository.findHotel(hotelId).orElse(hotel));
    }

    @Override public List<RoomTypeResponse> partnerListRooms(UUID actor, UUID hotelId) {
        HotelProfile hotel = hotelRequired(hotelId); authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor,"HOTEL",hotelId,"HOTEL_READ");
        return repository.findRoomTypes(hotelId, true).stream().map(this::roomResponse).toList();
    }

    @Override
    @Transactional
    public RoomTypeResponse partnerCreateRoom(UUID actor, UUID hotelId, UpsertRoomTypeRequest request) {
        HotelProfile hotel = hotelRequired(hotelId); authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor,"HOTEL",hotelId,"ROOM_WRITE");
        validateRoom(request); LocalDateTime now = LocalDateTime.now();
        RoomType room = RoomType.builder().id(UUID.randomUUID()).hotelId(hotelId).code(normalizeCode(request.getCode()))
                .name(request.getName().trim()).description(blankToNull(request.getDescription())).maxAdults(request.getMaxAdults())
                .maxChildren(request.getMaxChildren()).maxOccupancy(request.getMaxOccupancy()).bedConfig(json(defaultList(request.getBedConfig())))
                .amenities(json(defaultList(request.getAmenities()))).images(json(defaultList(request.getImages())))
                .roomSizeSqm(request.getRoomSizeSqm()).totalUnits(request.getTotalUnits()).status(defaulted(request.getStatus(), "ENABLED"))
                .disabledReason(blankToNull(request.getDisabledReason())).dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now).build();
        try { repository.insertRoomType(room); } catch (DataIntegrityViolationException ex) { throw conflict("Room code already exists"); }
        LocalDate start = LocalDate.now(ZoneId.of(hotel.getTimezone())); LocalDate end = start.plusDays(DEFAULT_INVENTORY_DAYS - 1L);
        int changed = repository.upsertInventoryRange(room.getId(), start, end, room.getTotalUnits(), 0, false, null, null, false, false, actor, now);
        if (changed != DEFAULT_INVENTORY_DAYS) throw conflict("Could not initialize room inventory");
        historyService.record(hotel.getOrganizationId(), "ROOM_TYPE", room.getId(), "CREATED", room, List.of(), actor, actorType(actor), null);
        return roomResponse(room);
    }

    @Override
    @Transactional
    public RoomTypeResponse partnerUpdateRoom(UUID actor, UUID roomId, UpsertRoomTypeRequest request) {
        RoomType room = roomRequired(roomId); HotelProfile hotel = hotelRequired(room.getHotelId());
        authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor,"HOTEL",hotel.getId(),"ROOM_WRITE"); validateRoom(request);
        long expected = expected(request.getExpectedVersion(), room.getDataVersion());
        room.setCode(normalizeCode(request.getCode())); room.setName(request.getName().trim()); room.setDescription(blankToNull(request.getDescription()));
        room.setMaxAdults(request.getMaxAdults()); room.setMaxChildren(request.getMaxChildren()); room.setMaxOccupancy(request.getMaxOccupancy());
        room.setBedConfig(json(defaultList(request.getBedConfig()))); room.setAmenities(json(defaultList(request.getAmenities())));
        room.setImages(json(defaultList(request.getImages()))); room.setRoomSizeSqm(request.getRoomSizeSqm()); room.setTotalUnits(request.getTotalUnits());
        room.setStatus(defaulted(request.getStatus(), room.getStatus())); room.setDisabledReason(blankToNull(request.getDisabledReason()));
        room.setDataVersion(expected); room.setUpdatedBy(actor); room.setUpdatedAt(LocalDateTime.now());
        try { optimistic(repository.updateRoomType(room), "Room type"); } catch (DataIntegrityViolationException ex) { throw conflict("Room update violates existing inventory or code"); }
        LocalDate start=LocalDate.now(ZoneId.of(hotel.getTimezone())); LocalDate end=start.plusDays(DEFAULT_INVENTORY_DAYS-1L);
        int changed=repository.upsertInventoryRange(roomId,start,end,room.getTotalUnits(),null,null,null,null,null,null,actor,room.getUpdatedAt());
        if(changed!=DEFAULT_INVENTORY_DAYS) throw conflict("New room total is lower than allocated inventory");
        room.setDataVersion(expected+1); historyService.record(hotel.getOrganizationId(),"ROOM_TYPE",roomId,"UPDATED",room,List.of("PROFILE","TOTAL_UNITS"),actor,actorType(actor),null);
        return roomResponse(room);
    }

    @Override public List<RatePlanResponse> partnerListRates(UUID actor, UUID roomId) {
        RoomType room=roomRequired(roomId); HotelProfile hotel=hotelRequired(room.getHotelId()); authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotel.getId(),"HOTEL_READ");
        return repository.findRatePlans(roomId,true).stream().map(this::rateResponse).toList();
    }

    @Override
    @Transactional
    public RatePlanResponse partnerCreateRate(UUID actor, UUID roomId, UpsertRatePlanRequest request) {
        RoomType room=roomRequired(roomId); HotelProfile hotel=hotelRequired(room.getHotelId()); authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotel.getId(),"RATE_WRITE");
        validateRate(request); LocalDateTime now=LocalDateTime.now();
        RatePlan rate=RatePlan.builder().id(UUID.randomUUID()).roomTypeId(roomId).code(normalizeCode(request.getCode())).name(request.getName().trim())
                .currency(request.getCurrency().toUpperCase()).basePrice(request.getBasePrice()).mealPlan(defaulted(request.getMealPlan(),"ROOM_ONLY"))
                .cancellationPolicy(json(defaultMap(request.getCancellationPolicy()))).occupancyPricing(json(defaultMap(request.getOccupancyPricing())))
                .minStay(request.getMinStay()).maxStay(request.getMaxStay()).status(defaulted(request.getStatus(),"ENABLED"))
                .dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now).build();
        try {repository.insertRatePlan(rate);} catch(DataIntegrityViolationException ex){throw conflict("Rate code already exists");}
        historyService.record(hotel.getOrganizationId(),"RATE_PLAN",rate.getId(),"CREATED",rate,List.of(),actor,actorType(actor),null); return rateResponse(rate);
    }

    @Override
    @Transactional
    public RatePlanResponse partnerUpdateRate(UUID actor, UUID rateId, UpsertRatePlanRequest request) {
        RatePlan rate=rateRequired(rateId); RoomType room=roomRequired(rate.getRoomTypeId()); HotelProfile hotel=hotelRequired(room.getHotelId());
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotel.getId(),"RATE_WRITE"); validateRate(request); long expected=expected(request.getExpectedVersion(),rate.getDataVersion());
        rate.setCode(normalizeCode(request.getCode()));rate.setName(request.getName().trim());rate.setCurrency(request.getCurrency().toUpperCase());
        rate.setBasePrice(request.getBasePrice());rate.setMealPlan(defaulted(request.getMealPlan(),rate.getMealPlan()));
        rate.setCancellationPolicy(json(defaultMap(request.getCancellationPolicy())));rate.setOccupancyPricing(json(defaultMap(request.getOccupancyPricing())));
        rate.setMinStay(request.getMinStay());rate.setMaxStay(request.getMaxStay());rate.setStatus(defaulted(request.getStatus(),rate.getStatus()));
        rate.setDataVersion(expected);rate.setUpdatedBy(actor);rate.setUpdatedAt(LocalDateTime.now());
        try{optimistic(repository.updateRatePlan(rate),"Rate plan");}catch(DataIntegrityViolationException ex){throw conflict("Rate update violates code or stay constraints");}
        rate.setDataVersion(expected+1);historyService.record(hotel.getOrganizationId(),"RATE_PLAN",rateId,"UPDATED",rate,List.of("RATE"),actor,actorType(actor),null);return rateResponse(rate);
    }

    @Override public List<RoomInventoryResponse> partnerGetInventory(UUID actor,UUID roomId,LocalDate start,LocalDate end){
        HotelProfile hotel=hotelForRoom(roomId);authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotel.getId(),"HOTEL_READ");validateRange(start,end,730);
        return repository.findInventory(roomId,start,end).stream().map(this::inventoryResponse).toList();
    }

    @Override
    @Transactional
    public List<RoomInventoryResponse> partnerUpdateInventory(UUID actor,UUID roomId,BulkUpdateRoomInventoryRequest request){
        HotelProfile hotel=hotelForRoom(roomId);authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotel.getId(),"INVENTORY_WRITE");validateRange(request.getStartDate(),request.getEndDate(),730);
        int expectedDays=(int)ChronoUnit.DAYS.between(request.getStartDate(),request.getEndDate())+1;LocalDateTime now=LocalDateTime.now();
        int changed=repository.upsertInventoryRange(roomId,request.getStartDate(),request.getEndDate(),request.getTotalUnits(),request.getBlockedUnits(),
                request.getStopSell(),request.getPriceOverride(),request.getMinStay(),request.getClosedToArrival(),request.getClosedToDeparture(),actor,now);
        if(changed!=expectedDays)throw conflict("Inventory update would make allocated units exceed total units");
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("roomTypeId",roomId);snapshot.put("request",request);
        historyService.record(hotel.getOrganizationId(),"ROOM_INVENTORY",roomId,"BULK_UPDATED",snapshot,List.of("DATE_RANGE"),actor,actorType(actor),null);
        return repository.findInventory(roomId,request.getStartDate(),request.getEndDate()).stream().map(this::inventoryResponse).toList();
    }

    @Override
    @Transactional
    public HotelBookingResponse createBooking(UUID userId,CreateHotelBookingRequest request){
        validateStay(request.getCheckInDate(),request.getCheckOutDate());HotelProfile hotel=hotelRequired(request.getHotelId());
        if(!organizationBookable(hotel.getOrganizationId()))throw notFound("Bookable hotel rate not found");
        RoomType room=roomRequired(request.getRoomTypeId());RatePlan rate=rateRequired(request.getRatePlanId());
        if(!hotel.getId().equals(room.getHotelId())||!room.getId().equals(rate.getRoomTypeId())||!"ENABLED".equals(hotel.getStatus())
                ||!"ENABLED".equals(room.getStatus())||!"ENABLED".equals(rate.getStatus()))throw notFound("Bookable hotel rate not found");
        int quantity=request.getQuantity();if(request.getAdults()>room.getMaxAdults()*quantity||request.getChildren()>room.getMaxChildren()*quantity
                ||request.getAdults()+request.getChildren()>room.getMaxOccupancy()*quantity)throw badRequest("Guest count exceeds room capacity");
        List<HotelAvailabilityDay> days=repository.findAvailability(hotel.getId(),room.getId(),rate.getId(),request.getCheckInDate(),request.getCheckOutDate());
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(request.getCheckInDate(),request.getCheckOutDate()));
        if(days.size()!=nights||days.stream().anyMatch(d->Boolean.TRUE.equals(d.getStopSell())||d.getAvailableUnits()<quantity))throw conflict("Selected room is no longer available");
        if(Boolean.TRUE.equals(days.get(0).getClosedToArrival()))throw conflict("Arrival is closed for selected date");
        int minimum=days.stream().map(HotelAvailabilityDay::getMinStay).filter(Objects::nonNull).max(Integer::compareTo).orElse(1);
        if(nights<minimum||rate.getMaxStay()!=null&&nights>rate.getMaxStay())throw badRequest("Stay length is outside rate plan limits");
        BigDecimal subtotal=days.stream().map(HotelAvailabilityDay::getNightlyPrice).reduce(BigDecimal.ZERO,BigDecimal::add).multiply(BigDecimal.valueOf(quantity));
        LocalDateTime now=LocalDateTime.now();int reserved=repository.reserveInventory(room.getId(),request.getCheckInDate(),request.getCheckOutDate(),quantity,userId,now);
        if(reserved!=nights)throw conflict("Selected room was just booked by another guest");
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("hotel",hotel);snapshot.put("roomType",room);snapshot.put("ratePlan",rate);snapshot.put("nightlyRates",days);
        HotelBooking booking=HotelBooking.builder().id(UUID.randomUUID()).bookingCode(bookingCode()).userId(userId).organizationId(hotel.getOrganizationId())
                .hotelId(hotel.getId()).checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate()).adults(request.getAdults()).children(request.getChildren())
                .guestLead(json(request.getGuestLead())).currency(rate.getCurrency()).subtotalAmount(subtotal).taxAmount(BigDecimal.ZERO).feeAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO).totalAmount(subtotal).bookingStatus("PENDING_PAYMENT").paymentStatus("UNPAID").source("GOROUTE")
                .snapshot(json(snapshot)).dataVersion(1L).createdBy(userId).updatedBy(userId).createdAt(now).updatedAt(now).build();
        HotelBookingItem item=HotelBookingItem.builder().id(UUID.randomUUID()).bookingId(booking.getId()).roomTypeId(room.getId()).ratePlanId(rate.getId())
                .quantity(quantity).adults(request.getAdults()).children(request.getChildren()).unitPrice(subtotal.divide(BigDecimal.valueOf(quantity),2,RoundingMode.HALF_UP))
                .totalPrice(subtotal).snapshot(json(snapshot)).createdAt(now).build();
        repository.insertBooking(booking);repository.insertBookingItem(item);
        historyService.record(hotel.getOrganizationId(),"HOTEL_BOOKING",booking.getId(),"CREATED",booking,List.of(),userId,"USER",null);
        return bookingResponse(repository.findBooking(booking.getId()).orElse(booking));
    }

    @Override public List<HotelBookingResponse> listMyBookings(UUID userId,int page,int size){PageRange r=pageRange(page,size);return repository.findBookingsByUser(userId,r.limit(),r.offset()).stream().map(this::bookingResponse).toList();}
    @Override public HotelBookingResponse getMyBooking(UUID userId,UUID bookingId){HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();return bookingResponse(b);}
    @Override @Transactional public HotelBookingResponse cancelMyBooking(UUID userId,UUID bookingId,String reason,Long version){HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();return transitionBooking(b,"CANCELLED_BY_GUEST",reason,version,userId,"USER");}

    @Override public List<HotelBookingResponse> partnerListBookings(UUID actor,UUID organizationId,String status,int page,int size){authorizationService.requireOrganization(organizationId,actor);PageRange r=pageRange(page,size);return repository.findBookingsByOrganization(organizationId,blankToNull(status),r.limit(),r.offset()).stream().filter(b->authorizationService.hasResourcePermission(organizationId,actor,"HOTEL",b.getHotelId(),"BOOKING_READ")).map(this::bookingResponse).toList();}
    @Override public HotelBookingResponse partnerGetBooking(UUID actor,UUID bookingId){HotelBooking b=bookingRequired(bookingId);authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_READ");return bookingResponse(b);}
    @Override @Transactional public HotelBookingResponse partnerUpdateBookingStatus(UUID actor,UUID bookingId,UpdateHotelBookingStatusRequest request){HotelBooking b=bookingRequired(bookingId);authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_WRITE");return transitionBooking(b,request.getBookingStatus(),request.getReason(),request.getExpectedVersion(),actor,"USER");}

    @Override public List<HotelProfileResponse> adminListHotels(String q,String status,int page,int size){PageRange r=pageRange(page,size);return repository.findHotelsAdmin(blankToNull(q),blankToNull(status),r.limit(),r.offset()).stream().map(this::hotelResponse).toList();}
    @Override public HotelProfileResponse adminGetHotel(UUID hotelId){return hotelResponse(hotelRequired(hotelId));}
    @Override public List<RoomTypeResponse> adminListRooms(UUID hotelId){hotelRequired(hotelId);return repository.findRoomTypes(hotelId,true).stream().map(this::roomResponse).toList();}
    @Override public List<RatePlanResponse> adminListRates(UUID roomId){roomRequired(roomId);return repository.findRatePlans(roomId,true).stream().map(this::rateResponse).toList();}
    @Override public List<RoomInventoryResponse> adminGetInventory(UUID roomId,LocalDate start,LocalDate end){roomRequired(roomId);validateRange(start,end,730);return repository.findInventory(roomId,start,end).stream().map(this::inventoryResponse).toList();}
    @Override @Transactional public HotelProfileResponse adminUpdateHotelStatus(UUID hotelId,String status,String reason){if(!Set.of("ENABLED","DISABLED","ARCHIVED","SUSPENDED").contains(status))throw badRequest("Invalid hotel status");HotelProfile h=hotelRequired(hotelId);h.setStatus(status);h.setDisabledReason("ENABLED".equals(status)?null:blankToNull(reason));h.setUpdatedAt(LocalDateTime.now());h.setUpdatedBy(null);optimistic(repository.updateHotel(h),"Hotel");h.setDataVersion(h.getDataVersion()+1);historyService.record(h.getOrganizationId(),"HOTEL",h.getId(),"ADMIN_STATUS_CHANGED",h,List.of("status"),null,"ADMIN",reason);return hotelResponse(repository.findHotel(hotelId).orElse(h));}
    @Override public List<HotelBookingResponse> adminListBookings(String q,String status,int page,int size){PageRange r=pageRange(page,size);return repository.findBookingsAdmin(blankToNull(q),blankToNull(status),r.limit(),r.offset()).stream().map(this::bookingResponse).toList();}
    @Override public HotelBookingResponse adminGetBooking(UUID bookingId){return bookingResponse(bookingRequired(bookingId));}
    @Override @Transactional public HotelBookingResponse adminUpdateBookingStatus(UUID bookingId,UpdateHotelBookingStatusRequest request){return transitionBooking(bookingRequired(bookingId),request.getBookingStatus(),request.getReason(),request.getExpectedVersion(),null,"ADMIN");}
    @Override public HotelProfileResponse adminCreateHotel(UUID actor,UpsertHotelRequest request){return partnerCreateHotel(actor,request);}
    @Override public HotelProfileResponse adminUpdateHotel(UUID actor,UUID hotelId,UpsertHotelRequest request){HotelProfile current=hotelRequired(hotelId);if("SUSPENDED".equals(current.getStatus())){adminUpdateHotelStatus(hotelId,"DISABLED","Admin editing suspended hotel");request.setExpectedVersion(hotelRequired(hotelId).getDataVersion());}return partnerUpdateHotel(actor,hotelId,request);}
    @Override public RoomTypeResponse adminCreateRoom(UUID actor,UUID hotelId,UpsertRoomTypeRequest request){return partnerCreateRoom(actor,hotelId,request);}
    @Override public RoomTypeResponse adminUpdateRoom(UUID actor,UUID roomId,UpsertRoomTypeRequest request){return partnerUpdateRoom(actor,roomId,request);}
    @Override public RatePlanResponse adminCreateRate(UUID actor,UUID roomId,UpsertRatePlanRequest request){return partnerCreateRate(actor,roomId,request);}
    @Override public RatePlanResponse adminUpdateRate(UUID actor,UUID rateId,UpsertRatePlanRequest request){return partnerUpdateRate(actor,rateId,request);}
    @Override public List<RoomInventoryResponse> adminUpdateInventory(UUID actor,UUID roomId,BulkUpdateRoomInventoryRequest request){return partnerUpdateInventory(actor,roomId,request);}

    private HotelBookingResponse transitionBooking(HotelBooking b,String target,String reason,Long requestedVersion,UUID actor,String actorType){
        validateTransition(b.getBookingStatus(),target);HotelBookingItem item=repository.findBookingItems(b.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(b.getCheckInDate(),b.getCheckOutDate()));LocalDateTime now=LocalDateTime.now();
        if("CONFIRMED".equals(target)){int n=repository.confirmReservedInventory(item.getRoomTypeId(),b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity(),actor,now);if(n!=nights)throw conflict("Reserved inventory is inconsistent");}
        else if(CANCELLATION_STATUSES.contains(target)){boolean reserved="PENDING_PAYMENT".equals(b.getBookingStatus());int n=repository.releaseInventory(item.getRoomTypeId(),b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity(),reserved,actor,now);if(n!=nights)throw conflict("Allocated inventory is inconsistent");}
        long version=expected(requestedVersion,b.getDataVersion());LocalDateTime cancelled=CANCELLATION_STATUSES.contains(target)?now:null;
        optimistic(repository.updateBookingStatus(b.getId(),version,target,null,blankToNull(reason),cancelled,actor,now),"Booking");
        HotelBooking saved=bookingRequired(b.getId());historyService.record(saved.getOrganizationId(),"HOTEL_BOOKING",saved.getId(),"STATUS_CHANGED",saved,List.of("bookingStatus"),actor,actorType,reason);return bookingResponse(saved);
    }

    private void validateTransition(String from,String to){
        Map<String,Set<String>> allowed=Map.of("PENDING_PAYMENT",Set.of("CONFIRMED","EXPIRED","FAILED","CANCELLED_BY_GUEST","CANCELLED_BY_HOST","CANCELLED_BY_PLATFORM"),
                "CONFIRMED",Set.of("CHECKED_IN","NO_SHOW","CANCELLED_BY_GUEST","CANCELLED_BY_HOST","CANCELLED_BY_PLATFORM"),"CHECKED_IN",Set.of("COMPLETED"));
        if(!allowed.getOrDefault(from,Set.of()).contains(to))throw badRequest("Invalid booking transition: "+from+" -> "+to);
    }
    private HotelBookingResponse bookingResponse(HotelBooking b){List<HotelBookingItemResponse> items=repository.findBookingItems(b.getId()).stream().map(i->HotelBookingItemResponse.builder().id(i.getId()).roomTypeId(i.getRoomTypeId()).ratePlanId(i.getRatePlanId()).roomTypeName(i.getRoomTypeName()).ratePlanName(i.getRatePlanName()).quantity(i.getQuantity()).adults(i.getAdults()).children(i.getChildren()).unitPrice(i.getUnitPrice()).totalPrice(i.getTotalPrice()).snapshot(readMap(i.getSnapshot())).build()).toList();return HotelBookingResponse.builder().id(b.getId()).bookingCode(b.getBookingCode()).userId(b.getUserId()).organizationId(b.getOrganizationId()).hotelId(b.getHotelId()).hotelName(b.getHotelName()).checkInDate(b.getCheckInDate()).checkOutDate(b.getCheckOutDate()).adults(b.getAdults()).children(b.getChildren()).guestLead(readMap(b.getGuestLead())).currency(b.getCurrency()).subtotalAmount(b.getSubtotalAmount()).taxAmount(b.getTaxAmount()).feeAmount(b.getFeeAmount()).discountAmount(b.getDiscountAmount()).totalAmount(b.getTotalAmount()).bookingStatus(b.getBookingStatus()).paymentStatus(b.getPaymentStatus()).source(b.getSource()).cancellationReason(b.getCancellationReason()).cancelledAt(b.getCancelledAt()).dataVersion(b.getDataVersion()).items(items).createdAt(b.getCreatedAt()).updatedAt(b.getUpdatedAt()).build();}
    private HotelProfileResponse hotelResponse(HotelProfile h){return HotelProfileResponse.builder().id(h.getId()).organizationId(h.getOrganizationId()).placeId(h.getPlaceId()).placeTitle(h.getPlaceTitle()).placeAddress(h.getPlaceAddress()).placeThumbnail(h.getPlaceThumbnail()).propertyCode(h.getPropertyCode()).propertyType(h.getPropertyType()).starRating(h.getStarRating()).description(h.getDescription()).checkInTime(h.getCheckInTime()).checkOutTime(h.getCheckOutTime()).timezone(h.getTimezone()).amenities(readList(h.getAmenities(),String.class)).policies(readMap(h.getPolicies())).bookingContact(readMap(h.getBookingContact())).status(h.getStatus()).disabledReason(h.getDisabledReason()).dataVersion(h.getDataVersion()).createdAt(h.getCreatedAt()).updatedAt(h.getUpdatedAt()).build();}
    private RoomTypeResponse roomResponse(RoomType r){return RoomTypeResponse.builder().id(r.getId()).hotelId(r.getHotelId()).code(r.getCode()).name(r.getName()).description(r.getDescription()).maxAdults(r.getMaxAdults()).maxChildren(r.getMaxChildren()).maxOccupancy(r.getMaxOccupancy()).bedConfig(readValue(r.getBedConfig(),new TypeReference<List<Map<String,Object>>>(){},List.of())).amenities(readList(r.getAmenities(),String.class)).images(readList(r.getImages(),String.class)).roomSizeSqm(r.getRoomSizeSqm()).totalUnits(r.getTotalUnits()).status(r.getStatus()).disabledReason(r.getDisabledReason()).dataVersion(r.getDataVersion()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();}
    private RatePlanResponse rateResponse(RatePlan r){return RatePlanResponse.builder().id(r.getId()).roomTypeId(r.getRoomTypeId()).code(r.getCode()).name(r.getName()).currency(r.getCurrency()).basePrice(r.getBasePrice()).mealPlan(r.getMealPlan()).cancellationPolicy(readMap(r.getCancellationPolicy())).occupancyPricing(readMap(r.getOccupancyPricing())).minStay(r.getMinStay()).maxStay(r.getMaxStay()).status(r.getStatus()).dataVersion(r.getDataVersion()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();}
    private RoomInventoryResponse inventoryResponse(RoomInventoryDaily i){return RoomInventoryResponse.builder().roomTypeId(i.getRoomTypeId()).inventoryDate(i.getInventoryDate()).totalUnits(i.getTotalUnits()).reservedUnits(i.getReservedUnits()).soldUnits(i.getSoldUnits()).blockedUnits(i.getBlockedUnits()).availableUnits(i.getAvailableUnits()).stopSell(i.getStopSell()).priceOverride(i.getPriceOverride()).minStay(i.getMinStay()).closedToArrival(i.getClosedToArrival()).closedToDeparture(i.getClosedToDeparture()).dataVersion(i.getDataVersion()).updatedAt(i.getUpdatedAt()).build();}

    private HotelProfile hotelRequired(UUID id){return repository.findHotel(id).orElseThrow(()->notFound("Hotel not found"));}
    private RoomType roomRequired(UUID id){return repository.findRoomType(id).orElseThrow(()->notFound("Room type not found"));}
    private RatePlan rateRequired(UUID id){return repository.findRatePlan(id).orElseThrow(()->notFound("Rate plan not found"));}
    private HotelBooking bookingRequired(UUID id){return repository.findBooking(id).orElseThrow(()->notFound("Hotel booking not found"));}
    private HotelProfile hotelForRoom(UUID roomId){return hotelRequired(roomRequired(roomId).getHotelId());}
    private boolean organizationBookable(UUID organizationId){return organizationRepository.findById(organizationId)
            .map(org->"ENABLED".equals(org.getOperationalStatus())&&!"SUSPENDED".equals(org.getVerificationStatus()))
            .orElse(false);}
    private void validateStay(LocalDate in,LocalDate out){if(in==null||out==null||!out.isAfter(in)||in.isBefore(LocalDate.now()))throw badRequest("Invalid check-in/check-out dates");long nights=ChronoUnit.DAYS.between(in,out);if(nights>MAX_BOOKING_NIGHTS)throw badRequest("Maximum stay is "+MAX_BOOKING_NIGHTS+" nights");}
    private void validateRange(LocalDate start,LocalDate end,int max){if(start==null||end==null||end.isBefore(start)||ChronoUnit.DAYS.between(start,end)+1>max)throw badRequest("Invalid date range");}
    private void validateRoom(UpsertRoomTypeRequest r){if(r.getMaxOccupancy()<r.getMaxAdults()||r.getMaxOccupancy()<1)throw badRequest("maxOccupancy must cover adults");}
    private void validateRate(UpsertRatePlanRequest r){if(r.getMaxStay()!=null&&r.getMaxStay()<r.getMinStay())throw badRequest("maxStay must be >= minStay");try{Currency.getInstance(r.getCurrency());}catch(Exception ex){throw badRequest("Invalid currency");}}
    private void validateTimezone(String v){try{ZoneId.of(v);}catch(Exception ex){throw badRequest("Invalid IANA timezone");}}
    private long expected(Long requested,Long current){return requested==null?current:requested;}
    private void optimistic(int n,String entity){if(n!=1)throw conflict(entity+" was changed by another user; reload and retry");}
    private String bookingCode(){return "HTL-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();}
    private String normalizeCode(String v){return v.trim().toUpperCase(Locale.ROOT);}
    private String defaulted(String v,String d){return v==null||v.isBlank()?d:v;}
    private String blankToNull(String v){return v==null||v.isBlank()?null:v.trim();}
    private <T> List<T> defaultList(List<T> v){return v==null?List.of():v;}
    private <K,V> Map<K,V> defaultMap(Map<K,V> v){return v==null?Map.of():v;}
    private String json(Object v){try{return objectMapper.writeValueAsString(v);}catch(JsonProcessingException ex){throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,"Cannot serialize marketplace data");}}
    private Map<String,Object> readMap(String v){return readValue(v,new TypeReference<Map<String,Object>>(){},Map.of());}
    private <T> List<T> readList(String v,Class<T> c){if(v==null||v.isBlank())return List.of();try{return objectMapper.readValue(v,objectMapper.getTypeFactory().constructCollectionType(List.class,c));}catch(Exception ex){return List.of();}}
    private <T>T readValue(String v,TypeReference<T> type,T fallback){if(v==null||v.isBlank())return fallback;try{return objectMapper.readValue(v,type);}catch(Exception ex){return fallback;}}
    private PageRange pageRange(int p,int s){int size=Math.min(Math.max(s,1),200);return new PageRange(size,Math.max(p,0)*size);}
    private BusinessException badRequest(String m){return new BusinessException(ErrorConstant.BAD_REQUEST,m);}
    private BusinessException conflict(String m){return new BusinessException(ErrorConstant.ALREADY_PROCESSED,m);}
    private BusinessException notFound(String m){return new BusinessException(ErrorConstant.NOT_FOUND,m);}
    private BusinessException forbidden(){return forbidden("You cannot access this booking");}
    private BusinessException forbidden(String message){return new BusinessException(ErrorConstant.FORBIDDEN_ERROR,message);}
    private String actorType(UUID actor){return actor!=null&&adminMapper.hasAnyRole(actor)?"ADMIN":"USER";}
    private record PageRange(int limit,int offset){}
}
