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
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import com.ds.goroute.type.MarketplaceBookingStatus;
import com.ds.goroute.type.MarketplacePaymentStatus;
import com.ds.goroute.type.MarketplacePublicationStatus;
import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationVerificationStatus;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.NotificationType;
import com.ds.goroute.type.OrganizationMemberStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class HotelMarketplaceServiceImpl implements HotelMarketplaceService {
    private static final int MAX_BOOKING_NIGHTS = 90;
    @Value("${goroute.marketplace.partner-confirmation-sla-minutes:120}")
    private long partnerConfirmationSlaMinutes = 120;
    private static final int DEFAULT_INVENTORY_DAYS = 730;
    private final HotelMarketplaceRepository repository;
    private final HostOrganizationRepository organizationRepository;
    private final PlaceRepository placeRepository;
    private final PartnerAuthorizationService authorizationService;
    private final MarketplaceHistoryService historyService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final AdminMapper adminMapper;

    @Override public List<HotelProfileResponse> listPublic(String query, int page, int size) {
        PageRange range = pageRange(page, size);
        return repository.findHotelsPublic(blankToNull(query), range.limit(), range.offset()).stream().map(this::hotelResponse).toList();
    }

    @Override public HotelProfileResponse getPublic(UUID hotelId) {
        return hotelResponse(publicHotelRequired(hotelId));
    }

    @Override public List<RoomTypeResponse> listPublicRooms(UUID hotelId) {
        getPublic(hotelId);
        return repository.findRoomTypes(hotelId, false).stream().map(this::roomResponse).toList();
    }

    @Override public List<RatePlanResponse> listPublicRates(UUID roomTypeId) {
        RoomType room = roomRequired(roomTypeId);
        getPublic(room.getHotelId());
        if (!MarketplaceAvailabilityStatus.ENABLED.name().equals(room.getStatus())) throw notFound("Room type not found");
        return repository.findRatePlans(roomTypeId, false).stream().map(this::rateResponse).toList();
    }

    @Override public List<RoomInventoryResponse> getAvailability(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                                                 LocalDate checkIn, LocalDate checkOut, Integer requestedQuantity,
                                                                 Integer requestedAdults, Integer requestedChildren) {
        validateStay(checkIn, checkOut);
        int quantity=requestedQuantity==null?1:requestedQuantity; int adults=requestedAdults==null?1:requestedAdults; int children=requestedChildren==null?0:requestedChildren;
        RoomType room=roomRequired(roomTypeId); RatePlan rate=rateRequired(ratePlanId);
        if(quantity<1||adults<1||children<0||adults>room.getMaxAdults()*quantity||children>room.getMaxChildren()*quantity||adults+children>room.getMaxOccupancy()*quantity) throw badRequest("Guest count exceeds room capacity");
        List<HotelAvailabilityDay> days = repository.findAvailability(hotelId, roomTypeId, ratePlanId, checkIn, checkOut);
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        if (days.size() != nights) return List.of();
        BigDecimal quotedTotal=days.stream().map(day->nightlyQuote(rate,day.getNightlyPrice(),quantity,adults,children)).reduce(BigDecimal.ZERO,BigDecimal::add);
        return days.stream().map(day -> RoomInventoryResponse.builder().roomTypeId(roomTypeId)
                .inventoryDate(day.getInventoryDate()).availableUnits(day.getAvailableUnits())
                .stopSell(day.getStopSell()).priceOverride(day.getNightlyPrice()).minStay(day.getMinStay())
                .quotedNightlyPrice(nightlyQuote(rate,day.getNightlyPrice(),quantity,adults,children)).quotedTotal(quotedTotal)
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
        Place place = placeRepository.findById(request.getPlaceId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        if (place.getPlaceGroup() != PlaceGroup.ACCOMMODATION) {
            throw badRequest("A hotel must be linked to an ACCOMMODATION place");
        }
        String timezone = request.getTimezone() == null ? org.getTimezone() : request.getTimezone();
        validateTimezone(timezone);
        LocalDateTime now = LocalDateTime.now();
        HotelProfile hotel = HotelProfile.builder().id(UUID.randomUUID()).organizationId(org.getId()).placeId(request.getPlaceId())
                .propertyCode(blankToNull(request.getPropertyCode())).propertyType(enumNameOrDefault(request.getPropertyType(), "HOTEL"))
                .starRating(request.getStarRating()).description(blankToNull(request.getDescription()))
                .checkInTime(request.getCheckInTime()).checkOutTime(request.getCheckOutTime()).timezone(timezone)
                .amenities(json(defaultList(request.getAmenities()))).languages(json(defaultList(request.getLanguages())))
                .receptionHours(json(defaultMap(request.getReceptionHours()))).houseRules(json(defaultList(request.getHouseRules())))
                .accessibilityFeatures(json(defaultList(request.getAccessibilityFeatures()))).parkingDetails(json(defaultMap(request.getParkingDetails())))
                .policies(json(defaultMap(request.getPolicies())))
                .bookingContact(json(defaultMap(request.getBookingContact()))).status(enumNameOrDefault(request.getStatus(), MarketplacePublicationStatus.DRAFT))
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
        if (MarketplacePublicationStatus.SUSPENDED.name().equals(hotel.getStatus())) {
            throw forbidden("A suspended hotel can only be changed by an admin");
        }
        if (!hotel.getOrganizationId().equals(request.getOrganizationId()) || !hotel.getPlaceId().equals(request.getPlaceId())) {
            throw badRequest("organizationId and placeId are immutable");
        }
        String timezone = defaulted(request.getTimezone(), hotel.getTimezone()); validateTimezone(timezone);
        long expected = requiredVersion(request.getExpectedVersion());
        hotel.setPropertyCode(blankToNull(request.getPropertyCode())); hotel.setPropertyType(enumNameOrDefault(request.getPropertyType(), hotel.getPropertyType()));
        hotel.setStarRating(request.getStarRating()); hotel.setDescription(blankToNull(request.getDescription()));
        hotel.setCheckInTime(request.getCheckInTime()); hotel.setCheckOutTime(request.getCheckOutTime()); hotel.setTimezone(timezone);
        hotel.setAmenities(json(defaultList(request.getAmenities()))); hotel.setLanguages(json(defaultList(request.getLanguages())));
        hotel.setReceptionHours(json(defaultMap(request.getReceptionHours()))); hotel.setHouseRules(json(defaultList(request.getHouseRules())));
        hotel.setAccessibilityFeatures(json(defaultList(request.getAccessibilityFeatures()))); hotel.setParkingDetails(json(defaultMap(request.getParkingDetails())));
        hotel.setPolicies(json(defaultMap(request.getPolicies())));
        hotel.setBookingContact(json(defaultMap(request.getBookingContact()))); hotel.setStatus(enumNameOrDefault(request.getStatus(), hotel.getStatus()));
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
                .standardAdults(request.getStandardAdults()).maxChildren(request.getMaxChildren()).maxInfants(request.getMaxInfants())
                .maxOccupancy(request.getMaxOccupancy()).bedroomCount(request.getBedroomCount()).bathroomCount(request.getBathroomCount())
                .viewType(blankToNull(request.getViewType())).bathroomType(enumNameOrDefault(request.getBathroomType(),"PRIVATE"))
                .smokingAllowed(Boolean.TRUE.equals(request.getSmokingAllowed())).bedConfig(json(defaultList(request.getBedConfig())))
                .amenities(json(defaultList(request.getAmenities()))).accessibilityFeatures(json(defaultList(request.getAccessibilityFeatures())))
                .images(json(defaultList(request.getImages())))
                .roomSizeSqm(request.getRoomSizeSqm()).totalUnits(request.getTotalUnits()).status(enumNameOrDefault(request.getStatus(), MarketplaceAvailabilityStatus.ENABLED))
                .disabledReason(blankToNull(request.getDisabledReason())).dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now).build();
        try { repository.insertRoomType(room); } catch (DataIntegrityViolationException ex) { throw conflict("Room code already exists"); }
        LocalDate start = LocalDate.now(ZoneId.of(hotel.getTimezone())); LocalDate end = start.plusDays(DEFAULT_INVENTORY_DAYS - 1L);
        Map<LocalDate,Long> initialInventoryVersions=new LinkedHashMap<>();start.datesUntil(end.plusDays(1)).forEach(day->initialInventoryVersions.put(day,0L));
        int changed = repository.upsertInventoryRange(room.getId(), start, end, room.getTotalUnits(), 0, false, null, null, false, false, json(initialInventoryVersions), actor, now);
        if (changed != DEFAULT_INVENTORY_DAYS) throw conflict("Could not initialize room inventory");
        historyService.record(hotel.getOrganizationId(), "ROOM_TYPE", room.getId(), "CREATED", room, List.of(), actor, actorType(actor), null);
        return roomResponse(room);
    }

    @Override
    @Transactional
    public RoomTypeResponse partnerUpdateRoom(UUID actor, UUID roomId, UpsertRoomTypeRequest request) {
        RoomType room = roomRequired(roomId); HotelProfile hotel = hotelRequired(room.getHotelId());
        authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor,"HOTEL",hotel.getId(),"ROOM_WRITE"); validateRoom(request);
        long expected = requiredVersion(request.getExpectedVersion());
        room.setCode(normalizeCode(request.getCode())); room.setName(request.getName().trim()); room.setDescription(blankToNull(request.getDescription()));
        room.setMaxAdults(request.getMaxAdults()); room.setStandardAdults(request.getStandardAdults()); room.setMaxChildren(request.getMaxChildren());
        room.setMaxInfants(request.getMaxInfants()); room.setMaxOccupancy(request.getMaxOccupancy()); room.setBedroomCount(request.getBedroomCount());
        room.setBathroomCount(request.getBathroomCount()); room.setViewType(blankToNull(request.getViewType()));
        room.setBathroomType(enumNameOrDefault(request.getBathroomType(),room.getBathroomType())); room.setSmokingAllowed(Boolean.TRUE.equals(request.getSmokingAllowed()));
        room.setBedConfig(json(defaultList(request.getBedConfig()))); room.setAmenities(json(defaultList(request.getAmenities())));
        room.setAccessibilityFeatures(json(defaultList(request.getAccessibilityFeatures())));
        room.setImages(json(defaultList(request.getImages()))); room.setRoomSizeSqm(request.getRoomSizeSqm()); room.setTotalUnits(request.getTotalUnits());
        room.setStatus(enumNameOrDefault(request.getStatus(), room.getStatus())); room.setDisabledReason(blankToNull(request.getDisabledReason()));
        room.setDataVersion(expected); room.setUpdatedBy(actor); room.setUpdatedAt(LocalDateTime.now());
        try { optimistic(repository.updateRoomType(room), "Room type"); } catch (DataIntegrityViolationException ex) { throw conflict("Room update violates existing inventory or code"); }
        LocalDate start=LocalDate.now(ZoneId.of(hotel.getTimezone())); LocalDate end=start.plusDays(DEFAULT_INVENTORY_DAYS-1L);
        int changed=repository.upsertInventoryRange(roomId,start,end,room.getTotalUnits(),null,null,null,null,null,null,inventoryExpectedVersionsJson(roomId,start,end),actor,room.getUpdatedAt());
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
                .description(blankToNull(request.getDescription())).currency(request.getCurrency().toUpperCase()).basePrice(request.getBasePrice())
                .pricingModel(enumNameOrDefault(request.getPricingModel(),"STANDARD")).baseOccupancy(request.getBaseOccupancy())
                .extraAdultFee(request.getExtraAdultFee()).extraChildFee(request.getExtraChildFee()).mealPlan(enumNameOrDefault(request.getMealPlan(),"ROOM_ONLY"))
                .includedBenefits(json(defaultList(request.getIncludedBenefits()))).cancellationPolicy(json(defaultMap(request.getCancellationPolicy())))
                .prepaymentPolicy(json(defaultMap(request.getPrepaymentPolicy()))).noShowPolicy(json(defaultMap(request.getNoShowPolicy())))
                .occupancyPricing(json(defaultMap(request.getOccupancyPricing())))
                .minStay(request.getMinStay()).maxStay(request.getMaxStay()).minAdvanceDays(request.getMinAdvanceDays())
                .maxAdvanceDays(request.getMaxAdvanceDays()).refundable(!Boolean.FALSE.equals(request.getRefundable()))
                .status(enumNameOrDefault(request.getStatus(),MarketplaceAvailabilityStatus.ENABLED))
                .dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now).build();
        try {repository.insertRatePlan(rate);} catch(DataIntegrityViolationException ex){throw conflict("Rate code already exists");}
        historyService.record(hotel.getOrganizationId(),"RATE_PLAN",rate.getId(),"CREATED",rate,List.of(),actor,actorType(actor),null); return rateResponse(rate);
    }

    @Override
    @Transactional
    public RatePlanResponse partnerUpdateRate(UUID actor, UUID rateId, UpsertRatePlanRequest request) {
        RatePlan rate=rateRequired(rateId); RoomType room=roomRequired(rate.getRoomTypeId()); HotelProfile hotel=hotelRequired(room.getHotelId());
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotel.getId(),"RATE_WRITE"); validateRate(request); long expected=requiredVersion(request.getExpectedVersion());
        rate.setCode(normalizeCode(request.getCode()));rate.setName(request.getName().trim());rate.setDescription(blankToNull(request.getDescription()));rate.setCurrency(request.getCurrency().toUpperCase());
        rate.setBasePrice(request.getBasePrice());rate.setPricingModel(enumNameOrDefault(request.getPricingModel(),rate.getPricingModel()));rate.setBaseOccupancy(request.getBaseOccupancy());
        rate.setExtraAdultFee(request.getExtraAdultFee());rate.setExtraChildFee(request.getExtraChildFee());rate.setMealPlan(enumNameOrDefault(request.getMealPlan(),rate.getMealPlan()));
        rate.setIncludedBenefits(json(defaultList(request.getIncludedBenefits())));rate.setCancellationPolicy(json(defaultMap(request.getCancellationPolicy())));
        rate.setPrepaymentPolicy(json(defaultMap(request.getPrepaymentPolicy())));rate.setNoShowPolicy(json(defaultMap(request.getNoShowPolicy())));
        rate.setOccupancyPricing(json(defaultMap(request.getOccupancyPricing())));
        rate.setMinStay(request.getMinStay());rate.setMaxStay(request.getMaxStay());rate.setMinAdvanceDays(request.getMinAdvanceDays());rate.setMaxAdvanceDays(request.getMaxAdvanceDays());
        rate.setRefundable(!Boolean.FALSE.equals(request.getRefundable()));rate.setStatus(enumNameOrDefault(request.getStatus(),rate.getStatus()));
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
        List<LocalDate> targetDays=request.getStartDate().datesUntil(request.getEndDate().plusDays(1)).toList();
        if(request.getExpectedVersions()==null||targetDays.stream().anyMatch(day->!request.getExpectedVersions().containsKey(day)))throw badRequest("expectedVersions must contain every selected inventory date; use 0 for a new date");
        int expectedDays=targetDays.size();LocalDateTime now=LocalDateTime.now();
        int changed=repository.upsertInventoryRange(roomId,request.getStartDate(),request.getEndDate(),request.getTotalUnits(),request.getBlockedUnits(),
                request.getStopSell(),request.getPriceOverride(),request.getMinStay(),request.getClosedToArrival(),request.getClosedToDeparture(),json(request.getExpectedVersions()),actor,now);
        if(changed!=expectedDays)throw conflict("Inventory update was incomplete; reload because inventory changed or allocated units exceed total units");
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("roomTypeId",roomId);snapshot.put("request",request);
        historyService.record(hotel.getOrganizationId(),"ROOM_INVENTORY",roomId,"BULK_UPDATED",snapshot,List.of("DATE_RANGE"),actor,actorType(actor),null);
        return repository.findInventory(roomId,request.getStartDate(),request.getEndDate()).stream().map(this::inventoryResponse).toList();
    }

    @Override
    public List<RatePlanDailyRateResponse> partnerGetRateCalendar(UUID actor, UUID ratePlanId, LocalDate start, LocalDate end) {
        RatePlan rate = rateRequired(ratePlanId);
        HotelProfile hotel = hotelForRoom(rate.getRoomTypeId());
        authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor, "HOTEL", hotel.getId(), "HOTEL_READ");
        validateRange(start, end, 730);
        return rateCalendarResponses(rate, start, end);
    }

    @Override
    @Transactional
    public List<RatePlanDailyRateResponse> partnerUpdateRateCalendar(UUID actor, UUID ratePlanId, BulkUpdateRatePlanCalendarRequest request) {
        RatePlan rate = rateRequired(ratePlanId);
        HotelProfile hotel = hotelForRoom(rate.getRoomTypeId());
        authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor, "HOTEL", hotel.getId(), "RATE_WRITE");
        validateRange(request.getStartDate(), request.getEndDate(), 730);
        validateRateCalendar(request);
        List<Integer> daysOfWeek = request.getDaysOfWeek() == null ? List.of() : request.getDaysOfWeek().stream().map(java.time.DayOfWeek::getValue).sorted().toList();
        List<LocalDate> targetDays = request.getStartDate().datesUntil(request.getEndDate().plusDays(1))
                .filter(day -> daysOfWeek.isEmpty() || daysOfWeek.contains(day.getDayOfWeek().getValue())).toList();
        if (request.getExpectedVersions() == null || targetDays.stream().anyMatch(day -> !request.getExpectedVersions().containsKey(day)))
            throw badRequest("expectedVersions must contain every selected calendar date; use 0 for a new date");
        long expectedDays = targetDays.size();
        int changed = repository.upsertRatePlanDailyRange(ratePlanId, request.getStartDate(), request.getEndDate(), daysOfWeek,
                request.getPrice(), request.getStopSell(), request.getMinStay(), request.getMaxStay(),
                request.getClosedToArrival(), request.getClosedToDeparture(), request.getMinAdvanceDays(),
                request.getMaxAdvanceDays(), json(request.getExpectedVersions()), json(request.getClearFields() == null ? Set.of() : request.getClearFields()), actor, LocalDateTime.now());
        if (changed != expectedDays) throw conflict("Rate calendar update was incomplete");
        Map<String, Object> snapshot = new LinkedHashMap<>(); snapshot.put("ratePlanId", ratePlanId); snapshot.put("request", request);
        historyService.record(hotel.getOrganizationId(), "RATE_CALENDAR", ratePlanId, "BULK_UPDATED", snapshot,
                List.of("DATE_RANGE"), actor, actorType(actor), null);
        return rateCalendarResponses(rate, request.getStartDate(), request.getEndDate());
    }

    @Override
    @Transactional
    public HotelBookingResponse createBooking(UUID userId,CreateHotelBookingRequest request){
        String idempotencyKey=blankToNull(request.getIdempotencyKey());
        if(idempotencyKey!=null){HotelBooking existing=repository.findBookingByUserAndIdempotencyKey(userId,idempotencyKey).orElse(null);if(existing!=null)return bookingResponse(existing);}
        validateStay(request.getCheckInDate(),request.getCheckOutDate());HotelProfile hotel=publicHotelRequired(request.getHotelId());
        RoomType room=roomRequired(request.getRoomTypeId());RatePlan rate=rateRequired(request.getRatePlanId());
        if(!hotel.getId().equals(room.getHotelId())||!room.getId().equals(rate.getRoomTypeId())||!MarketplacePublicationStatus.ENABLED.name().equals(hotel.getStatus())
                ||!MarketplaceAvailabilityStatus.ENABLED.name().equals(room.getStatus())||!MarketplaceAvailabilityStatus.ENABLED.name().equals(rate.getStatus()))throw notFound("Bookable hotel rate not found");
        int quantity=request.getQuantity();if(request.getAdults()>room.getMaxAdults()*quantity||request.getChildren()>room.getMaxChildren()*quantity
                ||request.getAdults()+request.getChildren()>room.getMaxOccupancy()*quantity)throw badRequest("Guest count exceeds room capacity");
        List<HotelAvailabilityDay> days=repository.findAvailability(hotel.getId(),room.getId(),rate.getId(),request.getCheckInDate(),request.getCheckOutDate());
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(request.getCheckInDate(),request.getCheckOutDate()));
        if(days.size()!=nights||days.stream().anyMatch(d->Boolean.TRUE.equals(d.getStopSell())||d.getAvailableUnits()<quantity))throw conflict("Selected room is no longer available");
        if(Boolean.TRUE.equals(days.get(0).getClosedToArrival()))throw conflict("Arrival is closed for selected date");
        if(Boolean.TRUE.equals(days.get(nights-1).getClosedToDeparture()))throw conflict("Departure is closed for selected date");
        int minimum=days.stream().map(HotelAvailabilityDay::getMinStay).filter(Objects::nonNull).max(Integer::compareTo).orElse(1);
        int maximum=days.stream().map(HotelAvailabilityDay::getMaxStay).filter(Objects::nonNull).min(Integer::compareTo).orElse(Integer.MAX_VALUE);
        if(nights<minimum||nights>maximum)throw badRequest("Stay length is outside rate plan limits");
        int advanceDays=daysUntilCheckIn(hotel,request.getCheckInDate());
        int minimumAdvance=Optional.ofNullable(days.get(0).getMinAdvanceDays()).orElse(0);
        Integer maximumAdvance=days.get(0).getMaxAdvanceDays();
        if(advanceDays<minimumAdvance||(maximumAdvance!=null&&advanceDays>maximumAdvance))throw badRequest("Check-in date is outside the rate plan booking window");
        BigDecimal subtotal=days.stream().map(day->nightlyQuote(rate,day.getNightlyPrice(),quantity,request.getAdults(),request.getChildren())).reduce(BigDecimal.ZERO,BigDecimal::add);
        LocalDateTime now=LocalDateTime.now();int reserved=repository.reserveInventory(room.getId(),rate.getId(),request.getCheckInDate(),request.getCheckOutDate(),quantity,userId,now);
        if(reserved!=nights)throw conflict("Selected room was just booked by another guest");
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("hotel",hotel);snapshot.put("roomType",room);snapshot.put("ratePlan",rate);snapshot.put("ratePlanVersion",rate.getDataVersion());snapshot.put("cancellationPolicy",readMap(rate.getCancellationPolicy()));snapshot.put("prepaymentPolicy",readMap(rate.getPrepaymentPolicy()));snapshot.put("noShowPolicy",readMap(rate.getNoShowPolicy()));snapshot.put("nightlyRates",days);
        HotelBooking booking=HotelBooking.builder().id(UUID.randomUUID()).bookingCode(bookingCode()).userId(userId).organizationId(hotel.getOrganizationId())
                .hotelId(hotel.getId()).checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate()).adults(request.getAdults()).children(request.getChildren())
                .guestLead(json(request.getGuestLead())).guestDetails(json(defaultList(request.getGuestDetails())))
                .specialRequests(blankToNull(request.getSpecialRequests())).estimatedArrivalTime(request.getEstimatedArrivalTime())
                .idempotencyKey(idempotencyKey).holdExpiresAt(partnerConfirmationDeadline(now))
                .currency(rate.getCurrency()).subtotalAmount(subtotal).taxAmount(BigDecimal.ZERO).feeAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO).totalAmount(subtotal).bookingStatus(MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name()).paymentStatus(MarketplacePaymentStatus.NOT_COLLECTED.name()).source("GOROUTE")
                .snapshot(json(snapshot)).dataVersion(1L).createdBy(userId).updatedBy(userId).createdAt(now).updatedAt(now).build();
        HotelBookingItem item=HotelBookingItem.builder().id(UUID.randomUUID()).bookingId(booking.getId()).roomTypeId(room.getId()).ratePlanId(rate.getId())
                .quantity(quantity).adults(request.getAdults()).children(request.getChildren()).unitPrice(subtotal.divide(BigDecimal.valueOf(quantity),2,RoundingMode.HALF_UP))
                .totalPrice(subtotal).snapshot(json(snapshot)).createdAt(now).build();
        repository.insertBooking(booking);repository.insertBookingItem(item);
        historyService.record(hotel.getOrganizationId(),"HOTEL_BOOKING",booking.getId(),"REQUESTED",booking,List.of(),userId,"USER",null);
        notifyPartnerRequest(hotel.getOrganizationId(), booking.getId(), booking.getBookingCode(), "hotel booking", booking.getHoldExpiresAt(), userId);
        return bookingResponse(repository.findBooking(booking.getId()).orElse(booking));
    }

    @Override public List<HotelBookingResponse> listMyBookings(UUID userId,int page,int size){PageRange r=pageRange(page,size);return repository.findBookingsByUser(userId,r.limit(),r.offset()).stream().map(this::bookingResponse).toList();}
    @Transactional public int expirePendingPaymentHolds(){LocalDateTime now=LocalDateTime.now();int expired=0;for(HotelBooking booking:repository.findExpiredPendingBookings(now,100)){if(repository.expireBookingHold(booking.getId(),booking.getDataVersion(),null,now)==1){HotelBookingItem item=repository.findBookingItems(booking.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));int nights=Math.toIntExact(ChronoUnit.DAYS.between(booking.getCheckInDate(),booking.getCheckOutDate()));if(repository.releaseInventory(item.getRoomTypeId(),booking.getCheckInDate(),booking.getCheckOutDate(),item.getQuantity(),true,null,now)!=nights)throw conflict("Expired booking inventory is inconsistent");historyService.record(booking.getOrganizationId(),"HOTEL_BOOKING",booking.getId(),"REQUEST_EXPIRED",booking,List.of("bookingStatus"),null,"SYSTEM","Partner confirmation deadline expired");expired++;}}return expired;}
    @Override public HotelBookingResponse getMyBooking(UUID userId,UUID bookingId){HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();return bookingResponse(b);}
    @Override @Transactional public HotelBookingResponse cancelMyBooking(UUID userId,UUID bookingId,String reason,Long version){HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();return transitionBooking(b,MarketplaceBookingStatus.CANCELLED_BY_GUEST,reason,version,userId,"USER");}

    @Override public List<HotelBookingResponse> partnerListBookings(UUID actor,UUID organizationId,String status,int page,int size){authorizationService.requireOrganization(organizationId,actor);PageRange r=pageRange(page,size);return repository.findBookingsByOrganization(organizationId,blankToNull(status),r.limit(),r.offset()).stream().filter(b->authorizationService.hasResourcePermission(organizationId,actor,"HOTEL",b.getHotelId(),"BOOKING_READ")).map(this::bookingResponse).toList();}
    @Override public HotelBookingResponse partnerGetBooking(UUID actor,UUID bookingId){HotelBooking b=bookingRequired(bookingId);authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_READ");return bookingResponse(b);}
    @Override @Transactional public HotelBookingResponse partnerUpdateBookingStatus(UUID actor,UUID bookingId,UpdateHotelBookingStatusRequest request){HotelBooking b=bookingRequired(bookingId);authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_WRITE");requireOperatorTarget(request.getBookingStatus());return transitionBooking(b,request.getBookingStatus(),request.getReason(),request.getExpectedVersion(),actor,"USER");}

    @Override public List<HotelProfileResponse> adminListHotels(String q,String status,int page,int size){PageRange r=pageRange(page,size);return repository.findHotelsAdmin(blankToNull(q),blankToNull(status),r.limit(),r.offset()).stream().map(this::hotelResponse).toList();}
    @Override public HotelProfileResponse adminGetHotel(UUID hotelId){return hotelResponse(hotelRequired(hotelId));}
    @Override public List<RoomTypeResponse> adminListRooms(UUID hotelId){hotelRequired(hotelId);return repository.findRoomTypes(hotelId,true).stream().map(this::roomResponse).toList();}
    @Override public List<RatePlanResponse> adminListRates(UUID roomId){roomRequired(roomId);return repository.findRatePlans(roomId,true).stream().map(this::rateResponse).toList();}
    @Override public List<RoomInventoryResponse> adminGetInventory(UUID roomId,LocalDate start,LocalDate end){roomRequired(roomId);validateRange(start,end,730);return repository.findInventory(roomId,start,end).stream().map(this::inventoryResponse).toList();}
    @Override public List<RatePlanDailyRateResponse> adminGetRateCalendar(UUID ratePlanId,LocalDate start,LocalDate end){RatePlan rate=rateRequired(ratePlanId);validateRange(start,end,730);return rateCalendarResponses(rate,start,end);}
    @Override @Transactional public HotelProfileResponse adminUpdateHotelStatus(UUID actor,UUID hotelId,MarketplacePublicationStatus status,String reason,Long expectedVersion){HotelProfile h=hotelRequired(hotelId);h.setStatus(status.name());h.setDisabledReason(status == MarketplacePublicationStatus.ENABLED?null:blankToNull(reason));h.setDataVersion(requiredVersion(expectedVersion));h.setUpdatedAt(LocalDateTime.now());h.setUpdatedBy(actor);optimistic(repository.updateHotel(h),"Hotel");h.setDataVersion(h.getDataVersion()+1);historyService.record(h.getOrganizationId(),"HOTEL",h.getId(),"ADMIN_STATUS_CHANGED",h,List.of("status"),actor,"ADMIN",reason);return hotelResponse(repository.findHotel(hotelId).orElse(h));}
    @Override public List<HotelBookingResponse> adminListBookings(String q,String status,int page,int size){PageRange r=pageRange(page,size);return repository.findBookingsAdmin(blankToNull(q),blankToNull(status),r.limit(),r.offset()).stream().map(this::bookingResponse).toList();}
    @Override public HotelBookingResponse adminGetBooking(UUID bookingId){return bookingResponse(bookingRequired(bookingId));}
    @Override @Transactional public HotelBookingResponse adminUpdateBookingStatus(UUID actor,UUID bookingId,UpdateHotelBookingStatusRequest request){requireOperatorTarget(request.getBookingStatus());return transitionBooking(bookingRequired(bookingId),request.getBookingStatus(),request.getReason(),request.getExpectedVersion(),actor,"ADMIN");}
    @Override @Transactional public HotelProfileResponse adminCreateHotel(UUID actor,UpsertHotelRequest request){return partnerCreateHotel(actor,request);}
    @Override @Transactional public HotelProfileResponse adminUpdateHotel(UUID actor,UUID hotelId,UpsertHotelRequest request){long expected=requiredVersion(request.getExpectedVersion());HotelProfile current=hotelRequired(hotelId);if(MarketplacePublicationStatus.SUSPENDED.name().equals(current.getStatus())){adminUpdateHotelStatus(actor,hotelId,MarketplacePublicationStatus.DISABLED,"Admin editing suspended hotel",expected);request.setExpectedVersion(expected+1);}return partnerUpdateHotel(actor,hotelId,request);}
    @Override @Transactional public RoomTypeResponse adminCreateRoom(UUID actor,UUID hotelId,UpsertRoomTypeRequest request){return partnerCreateRoom(actor,hotelId,request);}
    @Override @Transactional public RoomTypeResponse adminUpdateRoom(UUID actor,UUID roomId,UpsertRoomTypeRequest request){return partnerUpdateRoom(actor,roomId,request);}
    @Override @Transactional public RatePlanResponse adminCreateRate(UUID actor,UUID roomId,UpsertRatePlanRequest request){return partnerCreateRate(actor,roomId,request);}
    @Override @Transactional public RatePlanResponse adminUpdateRate(UUID actor,UUID rateId,UpsertRatePlanRequest request){return partnerUpdateRate(actor,rateId,request);}
    @Override @Transactional public List<RoomInventoryResponse> adminUpdateInventory(UUID actor,UUID roomId,BulkUpdateRoomInventoryRequest request){return partnerUpdateInventory(actor,roomId,request);}
    @Override @Transactional public List<RatePlanDailyRateResponse> adminUpdateRateCalendar(UUID actor,UUID ratePlanId,BulkUpdateRatePlanCalendarRequest request){return partnerUpdateRateCalendar(actor,ratePlanId,request);}

    private HotelBookingResponse transitionBooking(HotelBooking b,MarketplaceBookingStatus target,String reason,Long requestedVersion,UUID actor,String actorType){
        validateTransition(b.getBookingStatus(),target);validatePaymentCompatibility(b.getPaymentStatus(),target);if(target==MarketplaceBookingStatus.NO_SHOW) validateNoShowCutoff(b);HotelBookingItem item=repository.findBookingItems(b.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(b.getCheckInDate(),b.getCheckOutDate()));LocalDateTime now=LocalDateTime.now();
        if(target.confirmsReservedInventory()){int n=repository.confirmReservedInventory(item.getRoomTypeId(),b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity(),actor,now);if(n!=nights)throw conflict("Reserved inventory is inconsistent");}
        else if(target.releasesInventory()){boolean reserved=MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name().equals(b.getBookingStatus());int n=repository.releaseInventory(item.getRoomTypeId(),b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity(),reserved,actor,now);if(n!=nights)throw conflict("Allocated inventory is inconsistent");}
        long version=requiredVersion(requestedVersion);LocalDateTime cancelled=target.releasesInventory()?now:null;
        optimistic(repository.updateBookingStatus(b.getId(),version,target.name(),null,blankToNull(reason),cancelled,actor,now),"Booking");
        HotelBooking saved=bookingRequired(b.getId());historyService.record(saved.getOrganizationId(),"HOTEL_BOOKING",saved.getId(),"STATUS_CHANGED",saved,List.of("bookingStatus"),actor,actorType,reason);
        if(target==MarketplaceBookingStatus.CONFIRMED||target==MarketplaceBookingStatus.CANCELLED_BY_HOST) notifyGuestStatus(saved,target,reason,actor);
        return bookingResponse(saved);
    }

    private void validateTransition(String from,MarketplaceBookingStatus to){
        try { if(!MarketplaceBookingStatus.valueOf(from).canTransitionTo(to))throw badRequest("Invalid booking transition: "+from+" -> "+to); }
        catch(IllegalArgumentException ex){throw badRequest("Invalid stored booking status: "+from);}
    }
    private void validatePaymentCompatibility(String paymentStatus, MarketplaceBookingStatus bookingStatus){
        try { MarketplacePaymentStatus.requireCompatible(bookingStatus,paymentStatus); }
        catch(IllegalArgumentException ex){throw badRequest(ex.getMessage());}
    }
    private LocalDateTime partnerConfirmationDeadline(LocalDateTime now){
        if(partnerConfirmationSlaMinutes<15||partnerConfirmationSlaMinutes>24*60)throw new IllegalStateException("goroute.marketplace.partner-confirmation-sla-minutes must be between 15 and 1440");
        return now.plusMinutes(partnerConfirmationSlaMinutes);
    }
    private void validateNoShowCutoff(HotelBooking booking){
        HotelProfile hotel=hotelRequired(booking.getHotelId());
        ZoneId timezone;
        try { timezone=ZoneId.of(hotel.getTimezone()); } catch(Exception ex){throw badRequest("Hotel timezone is invalid; cannot mark no-show");}
        LocalTime cutoff=hotel.getCheckInTime()==null?LocalTime.of(23,59):hotel.getCheckInTime();
        if(LocalDateTime.now(timezone).isBefore(LocalDateTime.of(booking.getCheckInDate(),cutoff)))throw badRequest("A booking can be marked no-show only after its check-in cutoff");
    }
    private int daysUntilCheckIn(HotelProfile hotel, LocalDate checkIn){
        try{return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of(hotel.getTimezone())),checkIn));}
        catch(Exception ex){throw badRequest("Hotel timezone is invalid; cannot validate booking window");}
    }
    private String inventoryExpectedVersionsJson(UUID roomId,LocalDate start,LocalDate end){Map<LocalDate,Long> versions=new LinkedHashMap<>();repository.findInventory(roomId,start,end).forEach(row->versions.put(row.getInventoryDate(),row.getDataVersion()));start.datesUntil(end.plusDays(1)).forEach(day->versions.putIfAbsent(day,0L));return json(versions);}
    private void requireOperatorTarget(MarketplaceBookingStatus target){if(!target.canBeSetByOperator())throw badRequest("Only the guest can set CANCELLED_BY_GUEST");}
    private HotelBookingResponse bookingResponse(HotelBooking b){List<HotelBookingItemResponse> items=repository.findBookingItems(b.getId()).stream().map(i->HotelBookingItemResponse.builder().id(i.getId()).roomTypeId(i.getRoomTypeId()).ratePlanId(i.getRatePlanId()).roomTypeName(i.getRoomTypeName()).ratePlanName(i.getRatePlanName()).quantity(i.getQuantity()).adults(i.getAdults()).children(i.getChildren()).unitPrice(i.getUnitPrice()).totalPrice(i.getTotalPrice()).snapshot(readMap(i.getSnapshot())).build()).toList();return HotelBookingResponse.builder().id(b.getId()).bookingCode(b.getBookingCode()).userId(b.getUserId()).organizationId(b.getOrganizationId()).hotelId(b.getHotelId()).hotelName(b.getHotelName()).checkInDate(b.getCheckInDate()).checkOutDate(b.getCheckOutDate()).adults(b.getAdults()).children(b.getChildren()).guestLead(readMap(b.getGuestLead())).guestDetails(readValue(b.getGuestDetails(),new TypeReference<List<Map<String,Object>>>(){},List.of())).specialRequests(b.getSpecialRequests()).estimatedArrivalTime(b.getEstimatedArrivalTime()).holdExpiresAt(b.getHoldExpiresAt()).currency(b.getCurrency()).subtotalAmount(b.getSubtotalAmount()).taxAmount(b.getTaxAmount()).feeAmount(b.getFeeAmount()).discountAmount(b.getDiscountAmount()).totalAmount(b.getTotalAmount()).bookingStatus(b.getBookingStatus()).paymentStatus(b.getPaymentStatus()).source(b.getSource()).cancellationReason(b.getCancellationReason()).cancelledAt(b.getCancelledAt()).snapshot(readMap(b.getSnapshot())).dataVersion(b.getDataVersion()).items(items).createdAt(b.getCreatedAt()).updatedAt(b.getUpdatedAt()).build();}
    private HotelProfileResponse hotelResponse(HotelProfile h){return HotelProfileResponse.builder().id(h.getId()).organizationId(h.getOrganizationId()).placeId(h.getPlaceId()).placeTitle(h.getPlaceTitle()).placeAddress(h.getPlaceAddress()).placeThumbnail(h.getPlaceThumbnail()).propertyCode(h.getPropertyCode()).propertyType(h.getPropertyType()).starRating(h.getStarRating()).description(h.getDescription()).checkInTime(h.getCheckInTime()).checkOutTime(h.getCheckOutTime()).timezone(h.getTimezone()).amenities(readList(h.getAmenities(),String.class)).languages(readList(h.getLanguages(),String.class)).receptionHours(readMap(h.getReceptionHours())).houseRules(readList(h.getHouseRules(),String.class)).accessibilityFeatures(readList(h.getAccessibilityFeatures(),String.class)).parkingDetails(readMap(h.getParkingDetails())).policies(readMap(h.getPolicies())).bookingContact(readMap(h.getBookingContact())).status(h.getStatus()).disabledReason(h.getDisabledReason()).dataVersion(h.getDataVersion()).createdAt(h.getCreatedAt()).updatedAt(h.getUpdatedAt()).build();}
    private RoomTypeResponse roomResponse(RoomType r){return RoomTypeResponse.builder().id(r.getId()).hotelId(r.getHotelId()).code(r.getCode()).name(r.getName()).description(r.getDescription()).maxAdults(r.getMaxAdults()).standardAdults(r.getStandardAdults()).maxChildren(r.getMaxChildren()).maxInfants(r.getMaxInfants()).maxOccupancy(r.getMaxOccupancy()).bedroomCount(r.getBedroomCount()).bathroomCount(r.getBathroomCount()).viewType(r.getViewType()).bathroomType(r.getBathroomType()).smokingAllowed(r.getSmokingAllowed()).bedConfig(readValue(r.getBedConfig(),new TypeReference<List<Map<String,Object>>>(){},List.of())).amenities(readList(r.getAmenities(),String.class)).accessibilityFeatures(readList(r.getAccessibilityFeatures(),String.class)).images(readList(r.getImages(),String.class)).roomSizeSqm(r.getRoomSizeSqm()).totalUnits(r.getTotalUnits()).status(r.getStatus()).disabledReason(r.getDisabledReason()).dataVersion(r.getDataVersion()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();}
    private RatePlanResponse rateResponse(RatePlan r){return RatePlanResponse.builder().id(r.getId()).roomTypeId(r.getRoomTypeId()).code(r.getCode()).name(r.getName()).description(r.getDescription()).currency(r.getCurrency()).basePrice(r.getBasePrice()).pricingModel(r.getPricingModel()).baseOccupancy(r.getBaseOccupancy()).extraAdultFee(r.getExtraAdultFee()).extraChildFee(r.getExtraChildFee()).mealPlan(r.getMealPlan()).includedBenefits(readList(r.getIncludedBenefits(),String.class)).cancellationPolicy(readMap(r.getCancellationPolicy())).prepaymentPolicy(readMap(r.getPrepaymentPolicy())).noShowPolicy(readMap(r.getNoShowPolicy())).occupancyPricing(readMap(r.getOccupancyPricing())).minStay(r.getMinStay()).maxStay(r.getMaxStay()).minAdvanceDays(r.getMinAdvanceDays()).maxAdvanceDays(r.getMaxAdvanceDays()).refundable(r.getRefundable()).status(r.getStatus()).dataVersion(r.getDataVersion()).createdAt(r.getCreatedAt()).updatedAt(r.getUpdatedAt()).build();}
    private RoomInventoryResponse inventoryResponse(RoomInventoryDaily i){return RoomInventoryResponse.builder().roomTypeId(i.getRoomTypeId()).inventoryDate(i.getInventoryDate()).totalUnits(i.getTotalUnits()).reservedUnits(i.getReservedUnits()).soldUnits(i.getSoldUnits()).blockedUnits(i.getBlockedUnits()).availableUnits(i.getAvailableUnits()).stopSell(i.getStopSell()).priceOverride(i.getPriceOverride()).minStay(i.getMinStay()).closedToArrival(i.getClosedToArrival()).closedToDeparture(i.getClosedToDeparture()).dataVersion(i.getDataVersion()).updatedAt(i.getUpdatedAt()).build();}
    private List<RatePlanDailyRateResponse> rateCalendarResponses(RatePlan rate,LocalDate start,LocalDate end){Map<LocalDate,RatePlanDailyRate> overrides=repository.findRatePlanDailyRates(rate.getId(),start,end).stream().collect(java.util.stream.Collectors.toMap(RatePlanDailyRate::getRateDate,value->value));return start.datesUntil(end.plusDays(1)).map(day->{RatePlanDailyRate value=overrides.get(day);return RatePlanDailyRateResponse.builder().ratePlanId(rate.getId()).rateDate(day).price(value==null?null:value.getPrice()).effectivePrice(value!=null&&value.getPrice()!=null?value.getPrice():rate.getBasePrice()).stopSell(value!=null&&Boolean.TRUE.equals(value.getStopSell())).minStay(value!=null&&value.getMinStay()!=null?value.getMinStay():rate.getMinStay()).maxStay(value!=null&&value.getMaxStay()!=null?value.getMaxStay():rate.getMaxStay()).closedToArrival(value!=null&&Boolean.TRUE.equals(value.getClosedToArrival())).closedToDeparture(value!=null&&Boolean.TRUE.equals(value.getClosedToDeparture())).minAdvanceDays(value!=null&&value.getMinAdvanceDays()!=null?value.getMinAdvanceDays():rate.getMinAdvanceDays()).maxAdvanceDays(value!=null&&value.getMaxAdvanceDays()!=null?value.getMaxAdvanceDays():rate.getMaxAdvanceDays()).dataVersion(value==null?null:value.getDataVersion()).updatedAt(value==null?null:value.getUpdatedAt()).build();}).toList();}

    private HotelProfile hotelRequired(UUID id){return repository.findHotel(id).orElseThrow(()->notFound("Hotel not found"));}
    private HotelProfile publicHotelRequired(UUID id){return repository.findPublicHotel(id).orElseThrow(()->notFound("Hotel not found"));}
    private RoomType roomRequired(UUID id){return repository.findRoomType(id).orElseThrow(()->notFound("Room type not found"));}
    private RatePlan rateRequired(UUID id){return repository.findRatePlan(id).orElseThrow(()->notFound("Rate plan not found"));}
    private HotelBooking bookingRequired(UUID id){return repository.findBooking(id).orElseThrow(()->notFound("Hotel booking not found"));}
    private HotelProfile hotelForRoom(UUID roomId){return hotelRequired(roomRequired(roomId).getHotelId());}
    private boolean organizationBookable(UUID organizationId){return organizationRepository.findById(organizationId)
            .map(org->OrganizationOperationalStatus.ENABLED.name().equals(org.getOperationalStatus())&&OrganizationVerificationStatus.VERIFIED.name().equals(org.getVerificationStatus()))
            .orElse(false);}
    private void validateStay(LocalDate in,LocalDate out){if(in==null||out==null||!out.isAfter(in)||in.isBefore(LocalDate.now()))throw badRequest("Invalid check-in/check-out dates");long nights=ChronoUnit.DAYS.between(in,out);if(nights>MAX_BOOKING_NIGHTS)throw badRequest("Maximum stay is "+MAX_BOOKING_NIGHTS+" nights");}
    private BigDecimal nightlyQuote(RatePlan rate,BigDecimal nightlyPrice,int rooms,int adults,int children){
        BigDecimal total=nightlyPrice.multiply(BigDecimal.valueOf(rooms));
        int base=(rate.getBaseOccupancy()==null?1:rate.getBaseOccupancy())*rooms;
        int extraAdults=Math.max(0,adults-base);
        int extraChildren=Math.max(0,children);
        if(rate.getExtraAdultFee()!=null) total=total.add(rate.getExtraAdultFee().multiply(BigDecimal.valueOf(extraAdults)));
        if(rate.getExtraChildFee()!=null) total=total.add(rate.getExtraChildFee().multiply(BigDecimal.valueOf(extraChildren)));
        return total;
    }
    private void validateRange(LocalDate start,LocalDate end,int max){if(start==null||end==null||end.isBefore(start)||ChronoUnit.DAYS.between(start,end)+1>max)throw badRequest("Invalid date range");}
    private void validateRoom(UpsertRoomTypeRequest r){if(r.getMaxOccupancy()<r.getMaxAdults()||r.getMaxOccupancy()<1)throw badRequest("maxOccupancy must cover adults");if(r.getStandardAdults()>r.getMaxAdults())throw badRequest("standardAdults must be <= maxAdults");}
    private void validateRate(UpsertRatePlanRequest r){if(r.getMaxStay()!=null&&r.getMaxStay()<r.getMinStay())throw badRequest("maxStay must be >= minStay");if(r.getMaxAdvanceDays()!=null&&r.getMinAdvanceDays()!=null&&r.getMaxAdvanceDays()<r.getMinAdvanceDays())throw badRequest("maxAdvanceDays must be >= minAdvanceDays");try{Currency.getInstance(r.getCurrency());}catch(Exception ex){throw badRequest("Invalid currency");}}
    private void validateRateCalendar(BulkUpdateRatePlanCalendarRequest r){if(r.getMaxStay()!=null&&r.getMinStay()!=null&&r.getMaxStay()<r.getMinStay())throw badRequest("maxStay must be >= minStay");if(r.getMaxAdvanceDays()!=null&&r.getMinAdvanceDays()!=null&&r.getMaxAdvanceDays()<r.getMinAdvanceDays())throw badRequest("maxAdvanceDays must be >= minAdvanceDays");}
    private void validateTimezone(String v){try{ZoneId.of(v);}catch(Exception ex){throw badRequest("Invalid IANA timezone");}}
    private long requiredVersion(Long value){if(value==null||value<1)throw badRequest("expectedVersion is required for an update");return value;}
    private void optimistic(int n,String entity){if(n!=1)throw conflict(entity+" was changed by another user; reload and retry");}
    private String bookingCode(){return "HTL-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();}
    private String normalizeCode(String v){return v.trim().toUpperCase(Locale.ROOT);}
    private String enumNameOrDefault(Enum<?> value,Enum<?> fallback){return value==null?fallback.name():value.name();}
    private String enumNameOrDefault(Enum<?> value,String fallback){return value==null?fallback:value.name();}
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
    private void notifyPartnerRequest(UUID organizationId, UUID bookingId, String code, String kind, LocalDateTime deadline, UUID guestId){
        organizationRepository.findMembers(organizationId).stream().filter(member -> OrganizationMemberStatus.ACTIVE.name().equals(member.getMemberStatus()))
                .filter(member -> !member.getUserId().equals(guestId)).forEach(member -> notificationService.createNotification(member.getUserId(), null,
                        NotificationType.MARKETPLACE_BOOKING_REQUEST, "New " + kind + " request", code + " needs a response before " + deadline,
                        Map.of("bookingId", bookingId.toString(), "deepLink", "/partner/workspace?booking=" + bookingId), guestId));
    }
    private void notifyGuestStatus(HotelBooking booking, MarketplaceBookingStatus target, String reason, UUID actor){
        notificationService.createNotification(booking.getUserId(), null, target==MarketplaceBookingStatus.CONFIRMED?NotificationType.MARKETPLACE_BOOKING_CONFIRMED:NotificationType.MARKETPLACE_BOOKING_DECLINED,
                target==MarketplaceBookingStatus.CONFIRMED?"Booking confirmed":"Booking request declined", booking.getBookingCode() + (reason==null?"":": "+reason),
                Map.of("bookingId", booking.getId().toString(), "deepLink", "/marketplace/orders"), actor);
    }
    private record PageRange(int limit,int offset){}
}
