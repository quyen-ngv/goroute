package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.HotelMarketplaceRepository;
import com.ds.goroute.repository.MarketplacePromotionRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.HotelMarketplaceService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.marketplace.CancellationPolicyEvaluator;
import com.ds.goroute.service.marketplace.ListingReadiness;
import com.ds.goroute.service.marketplace.PromotionEngine;
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
    private static final int MAX_BOOKING_NIGHTS = com.ds.goroute.service.marketplace.HotelStayRules.MAX_BOOKING_NIGHTS;
    /** Hours after check-out during which a no-show can still be declared. */
    private static final int NO_SHOW_WINDOW_HOURS = 48;
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
    private final MarketplacePromotionRepository promotionRepository;
    private final com.ds.goroute.service.MarketplaceCommissionService commissionService;
    private final com.ds.goroute.service.marketplace.MarketplaceDisplayPrice displayPrice;
    private final com.ds.goroute.service.marketplace.HotelStayPricer stayPricer;
    private final com.ds.goroute.service.marketplace.HotelCatalogAssembler catalogAssembler;

    @Override public List<HotelProfileResponse> listPublic(HotelSearchQuery query, int page, int size) {
        PageRange r = pageRange(page, size);
        HotelSearchQuery q = query == null ? HotelSearchQuery.builder().build() : query;
        boolean stay = q.hasStay();
        int rooms = Math.max(1, q.getRooms() == null ? 1 : q.getRooms());
        int adults = Math.max(1, q.getAdults() == null ? 1 : q.getAdults());
        int children = Math.max(0, q.getChildren() == null ? 0 : q.getChildren());
        return repository.findHotelsPublic(blankToNull(q.getQuery()), blankToNull(q.getPropertyType()), q.getMinPrice(), q.getMaxPrice(),
                stay ? q.getCheckIn() : null, stay ? q.getCheckOut() : null, rooms, adults, children, r.limit(), r.offset())
                .stream().map(this::hotelResponse).map(this::displayed).toList();
    }

    @Override public HotelProfileResponse getPublic(UUID hotelId) {
        return displayed(catalogAssembler.toPublicDetail(publicHotelRequired(hotelId)));
    }

    @Override public List<RoomTypeResponse> listPublicRooms(UUID hotelId) {
        getPublic(hotelId);
        return repository.findRoomTypes(hotelId, false).stream().map(this::roomResponse).toList();
    }

    @Override public List<RatePlanResponse> listPublicRates(UUID roomTypeId) {
        RoomType room = roomRequired(roomTypeId);
        getPublic(room.getHotelId());
        if (!MarketplaceAvailabilityStatus.ENABLED.name().equals(room.getStatus())) throw notFound("Room type not found");
        return repository.findRatePlans(roomTypeId, false).stream().map(this::rateResponse).map(this::displayed).toList();
    }

    @Override public List<RoomInventoryResponse> getAvailability(UUID hotelId, UUID roomTypeId, UUID ratePlanId,
                                                                 LocalDate checkIn, LocalDate checkOut, Integer requestedQuantity,
                                                                 Integer requestedAdults, Integer requestedChildren) {
        validateStay(checkIn, checkOut);
        int quantity=requestedQuantity==null?1:requestedQuantity; int adults=requestedAdults==null?1:requestedAdults; int children=requestedChildren==null?0:requestedChildren;
        RoomType room=roomRequired(roomTypeId); RatePlan rate=rateRequired(ratePlanId);
        if(quantity<1||adults<1||children<0||adults>room.getMaxAdults()*quantity||children>room.getMaxChildren()*quantity||adults+children>room.getMaxOccupancy()*quantity) throw badRequest("Guest count exceeds room capacity");
        HotelProfile hotel=hotelRequired(hotelId); LocalDate today=propertyToday(hotel);
        List<HotelAvailabilityDay> days = repository.findAvailability(hotelId, roomTypeId, ratePlanId, checkIn, checkOut, today);
        int nights = Math.toIntExact(ChronoUnit.DAYS.between(checkIn, checkOut));
        if (days.size() != nights) return List.of();
        List<PromotionEngine.AppliedNight> priced=priceNights(hotel,rate,days,quantity,adults,children,nights,checkIn,today);
        BigDecimal quotedTotal=priced.stream().map(PromotionEngine.AppliedNight::price).reduce(BigDecimal.ZERO,BigDecimal::add);
        Map<LocalDate,PromotionEngine.AppliedNight> byDate=priced.stream().collect(java.util.stream.Collectors.toMap(PromotionEngine.AppliedNight::date,n->n));
        // The quote is what the guest compares against the browse price, so it is
        // converted the same way; the booking it leads to still settles in stayCurrency.
        String stayCurrency=rate.getCurrency();
        return days.stream().map(day -> {
            PromotionEngine.AppliedNight night=byDate.get(day.getInventoryDate());
            return RoomInventoryResponse.builder().roomTypeId(roomTypeId)
                .inventoryDate(day.getInventoryDate()).availableUnits(day.getAvailableUnits())
                .stopSell(day.getStopSell()).priceOverride(displayPrice.convert(day.getNightlyPrice(),stayCurrency)).minStay(day.getMinStay())
                .quotedNightlyPrice(displayPrice.convert(night.price(),stayCurrency)).originalNightlyPrice(night.discounted()?displayPrice.convert(night.originalPrice(),stayCurrency):null)
                .promotionCode(night.code()).promotionPercent(night.percent()).quotedTotal(displayPrice.convert(quotedTotal,stayCurrency))
                .closedToArrival(day.getClosedToArrival()).closedToDeparture(day.getClosedToDeparture()).build();
        }).toList();
    }

    @Override public List<HotelProfileResponse> partnerListHotels(UUID actor, UUID organizationId) {
        authorizationService.requireOrganization(organizationId, actor);
        return repository.findHotelsByOrganization(organizationId).stream()
                .filter(hotel->authorizationService.hasResourcePermission(organizationId,actor,"HOTEL",hotel.getId(),"HOTEL_READ"))
                .map(this::hotelResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HotelProfileResponse partnerGetHotel(UUID actor, UUID hotelId) {
        HotelProfile hotel = hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(), actor, "HOTEL", hotelId, "HOTEL_READ");
        return hotelResponse(hotel);
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
        validateOverviewYears(request);
        LocalDateTime now = LocalDateTime.now();
        HotelProfile hotel = HotelProfile.builder().id(UUID.randomUUID()).organizationId(org.getId()).placeId(request.getPlaceId())
                .propertyCode(blankToNull(request.getPropertyCode())).propertyType(enumNameOrDefault(request.getPropertyType(), "HOTEL"))
                .starRating(request.getStarRating()).description(blankToNull(request.getDescription()))
                .checkInTime(request.getCheckInTime()).checkOutTime(request.getCheckOutTime()).timezone(timezone)
                .amenities(json(defaultList(request.getAmenities()))).languages(json(defaultList(request.getLanguages())))
                .receptionHours(json(defaultMap(request.getReceptionHours()))).houseRules(json(defaultList(request.getHouseRules())))
                .accessibilityFeatures(json(defaultList(request.getAccessibilityFeatures()))).parkingDetails(json(defaultMap(request.getParkingDetails())))
                .policies(json(policiesOf(request)))
                .images(json(defaultList(request.getImages()))).openedYear(request.getOpenedYear()).renovatedYear(request.getRenovatedYear())
                .vatPercent(request.getVatPercent()).serviceChargePercent(request.getServiceChargePercent())
                .nearbyPlaces(json(defaultList(request.getNearbyPlaces())))
                .bookingContact(json(defaultMap(request.getBookingContact()))).status(enumNameOrDefault(request.getStatus(), MarketplacePublicationStatus.DRAFT))
                .disabledReason(blankToNull(request.getDisabledReason())).dataVersion(1L).createdBy(actor).updatedBy(actor)
                .createdAt(now).updatedAt(now).build();
        try { repository.insertHotel(hotel); } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED, "This place already has a hotel profile or property code is duplicated");
        }
        historyService.record(org.getId(), "HOTEL", hotel.getId(), "CREATED", hotel, List.of(), actor, actorType(actor), null);
        return hotelResponse(repository.findHotel(hotel.getId()).orElse(hotel));
    }

    /**
     * Changes a property's own fields. A hotel has <em>no title of its own</em> — the name a guest
     * reads is {@code places.title}, which {@code HotelProfileResponse} echoes back as
     * {@code placeTitle}. Sending {@code placeTitle} in this request therefore changes nothing and
     * still returns 200, which is exactly how the app's "change listing title" screen spent a
     * release saving into the void. Rename through {@code PUT /partner/places/{placeId}}.
     */
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
        hotel.setPolicies(json(policiesOf(request)));
        validateOverviewYears(request);
        hotel.setImages(json(defaultList(request.getImages()))); hotel.setOpenedYear(request.getOpenedYear()); hotel.setRenovatedYear(request.getRenovatedYear());
        hotel.setVatPercent(request.getVatPercent()); hotel.setServiceChargePercent(request.getServiceChargePercent());
        hotel.setNearbyPlaces(json(defaultList(request.getNearbyPlaces())));
        hotel.setBookingContact(json(defaultMap(request.getBookingContact())));
        String nextStatus = enumNameOrDefault(request.getStatus(), hotel.getStatus());
        if (MarketplacePublicationStatus.ENABLED.name().equals(nextStatus) && !MarketplacePublicationStatus.ENABLED.name().equals(hotel.getStatus())) {
            ListingReadiness.Result readiness = hotelReadiness(hotel);
            if (!readiness.ready()) throw badRequest("The listing is not ready to sell yet: " + String.join(", ", readiness.failingRequiredCodes()));
        }
        hotel.setStatus(nextStatus);
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
        Integer previousTotalUnits = room.getTotalUnits();
        room.setImages(json(defaultList(request.getImages()))); room.setRoomSizeSqm(request.getRoomSizeSqm()); room.setTotalUnits(request.getTotalUnits());
        room.setStatus(enumNameOrDefault(request.getStatus(), room.getStatus())); room.setDisabledReason(blankToNull(request.getDisabledReason()));
        room.setDataVersion(expected); room.setUpdatedBy(actor); room.setUpdatedAt(LocalDateTime.now());
        try { optimistic(repository.updateRoomType(room), "Room type"); } catch (DataIntegrityViolationException ex) { throw conflict("Room update violates existing inventory or code"); }
        // Only a change of the room count touches the calendar. Re-writing 730 days on every profile edit
        // used to erase per-date allotments the partner had set through the inventory screen.
        if(!Objects.equals(previousTotalUnits, room.getTotalUnits())){
            LocalDate start=LocalDate.now(ZoneId.of(hotel.getTimezone())); LocalDate end=start.plusDays(DEFAULT_INVENTORY_DAYS-1L);
            int changed=repository.upsertInventoryRange(roomId,start,end,room.getTotalUnits(),null,null,null,null,null,null,inventoryExpectedVersionsJson(roomId,start,end),actor,room.getUpdatedAt());
            if(changed!=DEFAULT_INVENTORY_DAYS) throw conflict("New room total is lower than allocated inventory");
        }
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
        rate.setRefundable(!Boolean.FALSE.equals(request.getRefundable()));
        String nextRateStatus=enumNameOrDefault(request.getStatus(),rate.getStatus());
        if(!MarketplaceAvailabilityStatus.ENABLED.name().equals(nextRateStatus)&&MarketplaceAvailabilityStatus.ENABLED.name().equals(rate.getStatus())){
            // Taken before the bookings are counted, so a guest booking this rate right now is
            // either already counted below or still waiting for this row -- never in between.
            repository.findRatePlanForUpdate(rateId);
            long live=repository.countActiveBookingsForRatePlan(rateId);
            // Closing a rate silently would leave guests holding a booking on a product that no longer exists.
            if(live>0)throw conflict("This rate plan still has "+live+" active booking(s). Cancel or complete them before closing it");
            promotionRepository.archiveForRatePlan(rateId,actor,LocalDateTime.now());
        }
        rate.setStatus(nextRateStatus);
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

    /**
     * A cart line is shown, not booked, so an unbookable selection is an answer rather than an error:
     * the rules below are the ones createBooking enforces, and their message is what the guest reads.
     * Deliberately not transactional and takes no lock — this is a read of the live calendar.
     */
    @Override
    public MarketplaceQuoteResponse quoteStay(UUID hotelId,UUID roomTypeId,UUID ratePlanId,LocalDate checkIn,LocalDate checkOut,
                                              int quantity,int adults,int children){
        try {
            HotelProfile hotel=publicHotelRequired(hotelId);RoomType room=roomRequired(roomTypeId);RatePlan rate=rateRequired(ratePlanId);
            if(!hotel.getId().equals(room.getHotelId())||!room.getId().equals(rate.getRoomTypeId())
                    ||!MarketplacePublicationStatus.ENABLED.name().equals(hotel.getStatus())
                    ||!MarketplaceAvailabilityStatus.ENABLED.name().equals(room.getStatus())
                    ||!MarketplaceAvailabilityStatus.ENABLED.name().equals(rate.getStatus()))throw notFound("Bookable hotel rate not found");
            BigDecimal total=priceStay(hotel,room,rate,checkIn,checkOut,quantity,adults,children,null,null,0);
            // priceStay is shared with change requests, which are exempt from the booking window;
            // a new booking is not, so the cart applies it here or a line would promise a stay
            // createBooking then refuses.
            requireWithinBookingWindow(hotel,room,rate,checkIn,checkOut);
            return MarketplaceQuoteResponse.builder().available(true).currency(rate.getCurrency()).totalAmount(total).build();
        } catch (BusinessException exception) {
            return MarketplaceQuoteResponse.builder().available(false).unavailableReason(exception.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public HotelBookingResponse createBooking(UUID userId,CreateHotelBookingRequest request){
        String idempotencyKey=blankToNull(request.getIdempotencyKey());
        if(idempotencyKey!=null){HotelBooking existing=repository.findBookingByUserAndIdempotencyKey(userId,idempotencyKey).orElse(null);if(existing!=null)return bookingResponse(existing);}
        HotelProfile hotel=publicHotelRequired(request.getHotelId());LocalDate today=propertyToday(hotel);validateStay(request.getCheckInDate(),request.getCheckOutDate(),today);
        // Locked, not just read: the partner may be closing this very rate plan. Whichever of the
        // two gets the row first wins, and the loser sees the winner's result -- this booking is
        // refused as not bookable, or the close is refused because this booking is now live.
        RoomType room=roomRequired(request.getRoomTypeId());RatePlan rate=rateRequiredForBooking(request.getRatePlanId());
        if(!hotel.getId().equals(room.getHotelId())||!room.getId().equals(rate.getRoomTypeId())||!MarketplacePublicationStatus.ENABLED.name().equals(hotel.getStatus())
                ||!MarketplaceAvailabilityStatus.ENABLED.name().equals(room.getStatus())||!MarketplaceAvailabilityStatus.ENABLED.name().equals(rate.getStatus()))throw notFound("Bookable hotel rate not found");
        int quantity=request.getQuantity();if(request.getAdults()>room.getMaxAdults()*quantity||request.getChildren()>room.getMaxChildren()*quantity
                ||request.getAdults()+request.getChildren()>room.getMaxOccupancy()*quantity)throw badRequest("Guest count exceeds room capacity");
        List<HotelAvailabilityDay> days=repository.findAvailability(hotel.getId(),room.getId(),rate.getId(),request.getCheckInDate(),request.getCheckOutDate(),today);
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(request.getCheckInDate(),request.getCheckOutDate()));
        if(days.size()!=nights||days.stream().anyMatch(d->Boolean.TRUE.equals(d.getStopSell())||d.getAvailableUnits()<quantity))throw conflict("Selected room is no longer available");
        if(Boolean.TRUE.equals(days.get(0).getClosedToArrival()))throw conflict("Arrival is closed for selected date");
        if(Boolean.TRUE.equals(days.get(nights-1).getClosedToDeparture()))throw conflict("Departure is closed for selected date");
        int minimum=days.stream().map(HotelAvailabilityDay::getMinStay).filter(Objects::nonNull).max(Integer::compareTo).orElse(1);
        int maximum=days.stream().map(HotelAvailabilityDay::getMaxStay).filter(Objects::nonNull).min(Integer::compareTo).orElse(Integer.MAX_VALUE);
        if(nights<minimum||nights>maximum)throw badRequest("Stay length is outside rate plan limits");
        int advanceDays=Math.toIntExact(ChronoUnit.DAYS.between(today,request.getCheckInDate()));
        int minimumAdvance=Optional.ofNullable(days.get(0).getMinAdvanceDays()).orElse(0);
        Integer maximumAdvance=days.get(0).getMaxAdvanceDays();
        if(advanceDays<minimumAdvance||(maximumAdvance!=null&&advanceDays>maximumAdvance))throw badRequest("Check-in date is outside the rate plan booking window");
        List<PromotionEngine.AppliedNight> priced=priceNights(hotel,rate,days,quantity,request.getAdults(),request.getChildren(),nights,request.getCheckInDate(),today);
        BigDecimal subtotal=priced.stream().map(PromotionEngine.AppliedNight::originalPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal discount=priced.stream().map(PromotionEngine.AppliedNight::discount).reduce(BigDecimal.ZERO,BigDecimal::add);
        BigDecimal payable=subtotal.subtract(discount);
        LocalDateTime now=LocalDateTime.now();int reserved=repository.reserveInventory(room.getId(),rate.getId(),request.getCheckInDate(),request.getCheckOutDate(),quantity,userId,now);
        if(reserved!=nights)throw conflict("Selected room was just booked by another guest");
        Map<String,Object> snapshot=new LinkedHashMap<>();snapshot.put("hotel",hotel);snapshot.put("roomType",room);snapshot.put("ratePlan",rate);snapshot.put("ratePlanVersion",rate.getDataVersion());snapshot.put("cancellationPolicy",readMap(rate.getCancellationPolicy()));snapshot.put("prepaymentPolicy",readMap(rate.getPrepaymentPolicy()));snapshot.put("noShowPolicy",readMap(rate.getNoShowPolicy()));snapshot.put("nightlyRates",days);snapshot.put("promotions",priced.stream().filter(PromotionEngine.AppliedNight::discounted).map(n->Map.of("date",n.date().toString(),"code",n.code(),"percent",n.percent(),"originalPrice",n.originalPrice(),"price",n.price())).toList());
        HotelBooking booking=HotelBooking.builder().id(UUID.randomUUID()).bookingCode(bookingCode()).userId(userId).organizationId(hotel.getOrganizationId())
                .hotelId(hotel.getId()).checkInDate(request.getCheckInDate()).checkOutDate(request.getCheckOutDate()).adults(request.getAdults()).children(request.getChildren())
                .guestLead(json(request.getGuestLead())).guestDetails(json(defaultList(request.getGuestDetails())))
                .specialRequests(blankToNull(request.getSpecialRequests())).estimatedArrivalTime(request.getEstimatedArrivalTime())
                .idempotencyKey(idempotencyKey).holdExpiresAt(partnerConfirmationDeadline(now))
                .currency(rate.getCurrency()).subtotalAmount(subtotal).taxAmount(BigDecimal.ZERO).feeAmount(BigDecimal.ZERO)
                .discountAmount(discount).totalAmount(payable).bookingStatus(MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name()).paymentStatus(MarketplacePaymentStatus.NOT_COLLECTED.name()).source("GOROUTE")
                .snapshot(json(snapshot)).dataVersion(1L).createdBy(userId).updatedBy(userId).createdAt(now).updatedAt(now).build();
        HotelBookingItem item=HotelBookingItem.builder().id(UUID.randomUUID()).bookingId(booking.getId()).roomTypeId(room.getId()).ratePlanId(rate.getId())
                .quantity(quantity).adults(request.getAdults()).children(request.getChildren()).unitPrice(payable.divide(BigDecimal.valueOf(quantity),2,RoundingMode.HALF_UP))
                .totalPrice(payable).snapshot(json(snapshot)).createdAt(now).build();
        try { repository.insertBooking(booking);repository.insertBookingItem(item); }
        catch(DataIntegrityViolationException ex){
            // Two requests raced on the same idempotency key. The other one holds the inventory; this
            // transaction rolls back (releasing what it reserved) and the client's retry finds the winner.
            throw conflict("This booking was already submitted; open My bookings to see it");
        }
        // The commission rate is frozen here, like the price snapshot: changing the organization rate later
        // must never re-price a booking a guest already made.
        commissionService.stampCommission("HOTEL", booking.getId());
        historyService.record(hotel.getOrganizationId(),"HOTEL_BOOKING",booking.getId(),"REQUESTED",booking,List.of(),userId,"USER",null);
        notifyPartnerRequest(hotel.getOrganizationId(), hotel.getId(), booking.getId(), booking.getBookingCode(), "hotel booking", booking.getHoldExpiresAt(), userId);
        return bookingResponse(repository.findBooking(booking.getId()).orElse(booking));
    }

    @Override public List<HotelBookingResponse> listMyBookings(UUID userId,int page,int size){PageRange r=pageRange(page,size);return repository.findBookingsByUser(userId,r.limit(),r.offset()).stream().map(this::bookingResponse).toList();}
    @Override public List<UUID> findExpiredPendingBookingIds(LocalDateTime now,int limit){return repository.findExpiredPendingBookings(now,limit).stream().map(HotelBooking::getId).toList();}
    @Override @Transactional public boolean expirePendingBooking(UUID bookingId,LocalDateTime now){
        HotelBooking booking=repository.findBooking(bookingId).orElse(null);
        if(booking==null||!MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name().equals(booking.getBookingStatus()))return false;
        if(repository.expireBookingHold(booking.getId(),booking.getDataVersion(),null,now)!=1)return false;
        HotelBookingItem item=repository.findBookingItems(booking.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(booking.getCheckInDate(),booking.getCheckOutDate()));
        // Throwing here rolls back only this booking (the job calls us per row) and keeps it PENDING for the next run,
        // which is still better than silently leaving inventory counted twice.
        if(repository.releaseInventory(item.getRoomTypeId(),booking.getCheckInDate(),booking.getCheckOutDate(),item.getQuantity(),true,null,now)!=nights)throw conflict("Expired booking inventory is inconsistent");
        historyService.record(booking.getOrganizationId(),"HOTEL_BOOKING",booking.getId(),"REQUEST_EXPIRED",booking,List.of("bookingStatus"),null,"SYSTEM","Partner confirmation deadline expired");
        notifyGuestLifecycle(booking,MarketplaceBookingStatus.EXPIRED,null,null);
        return true;
    }
    @Override public HotelBookingResponse getMyBooking(UUID userId,UUID bookingId){HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();return bookingResponse(b);}
    @Override public CancellationPreviewResponse previewMyCancellation(UUID userId,UUID bookingId){HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();return cancellationPreview(b);}
    @Override @Transactional public HotelBookingResponse cancelMyBooking(UUID userId,UUID bookingId,String reason,Long version){
        HotelBooking b=bookingRequired(bookingId);if(!userId.equals(b.getUserId()))throw forbidden();
        CancellationPreviewResponse preview=cancellationPreview(b);
        if(!preview.isCancellable())throw badRequest(preview.getBlockedReason());
        String outcome=preview.isFree()?"free cancellation":"penalty "+preview.getPenaltyAmount()+" "+preview.getCurrency()+" ("+preview.getPenaltyRule()+")";
        String recorded=(blankToNull(reason)==null?"Cancelled by guest":reason.trim())+" — "+outcome;
        return transitionBooking(b,MarketplaceBookingStatus.CANCELLED_BY_GUEST,recorded,version==null?b.getDataVersion():version,null,userId,"USER");
    }
    private CancellationPreviewResponse cancellationPreview(HotelBooking b){
        MarketplaceBookingStatus current;
        try{current=MarketplaceBookingStatus.valueOf(b.getBookingStatus());}catch(IllegalArgumentException ex){throw badRequest("Invalid stored booking status: "+b.getBookingStatus());}
        HotelProfile hotel=hotelRequired(b.getHotelId());
        ZoneId zone;try{zone=ZoneId.of(hotel.getTimezone());}catch(Exception ex){zone=ZoneId.systemDefault();}
        LocalDateTime start=LocalDateTime.of(b.getCheckInDate(),hotel.getCheckInTime()==null?com.ds.goroute.service.marketplace.HotelStayRules.DEFAULT_CHECK_IN_TIME:hotel.getCheckInTime());
        LocalDateTime now=LocalDateTime.now(zone);
        CancellationPreviewResponse.CancellationPreviewResponseBuilder out=CancellationPreviewResponse.builder().currency(b.getCurrency()).serviceStartsAt(start);
        if(!current.isGuestCancellable())return out.cancellable(false).blockedReason("This booking is "+current.name().toLowerCase(Locale.ROOT).replace('_',' ')+" and can no longer be cancelled").build();
        if(current==MarketplaceBookingStatus.CONFIRMED&&!now.isBefore(start))return out.cancellable(false).blockedReason("Online cancellation closed at check-in time; please contact the property").build();
        Map<String,Object> snapshot=readMap(b.getSnapshot());Object policy=snapshot.get("cancellationPolicy");
        @SuppressWarnings("unchecked") Map<String,Object> policyMap=policy instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
        long nights=Math.max(1,ChronoUnit.DAYS.between(b.getCheckInDate(),b.getCheckOutDate()));
        BigDecimal firstNight=b.getTotalAmount()==null?null:b.getTotalAmount().divide(BigDecimal.valueOf(nights),2,RoundingMode.HALF_UP);
        // A request the partner has not accepted yet is always free to withdraw.
        CancellationPolicyEvaluator.Outcome outcome=current==MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION
                ?new CancellationPolicyEvaluator.Outcome(String.valueOf(policyMap.getOrDefault("type","FLEXIBLE")),true,start,BigDecimal.ZERO,"PENDING_REQUEST")
                :CancellationPolicyEvaluator.evaluate(policyMap,start,now,b.getTotalAmount(),firstNight);
        return out.cancellable(true).policyType(outcome.policyType()).free(outcome.free()).freeUntil(outcome.freeUntil()).penaltyAmount(outcome.penaltyAmount()).penaltyRule(outcome.penaltyRule()).build();
    }

    @Override public PageResponse<HotelBookingResponse> partnerListBookings(UUID actor,UUID organizationId,String status,UUID hotelId,int page,int size){
        authorizationService.requireOrganization(organizationId,actor);
        // One property at a time is its own permission check, so a member scoped out of it gets 403 instead of an empty page;
        // otherwise the member scope is resolved once and pushed into SQL, so pages are full and the total is honest.
        List<UUID> hotelIds;
        if(hotelId!=null){authorizationService.requireResourcePermission(organizationId,actor,"HOTEL",hotelId,"BOOKING_READ");hotelIds=List.of(hotelId);}
        else hotelIds=authorizationService.accessibleResourceIds(organizationId,actor,"HOTEL",
                repository.findHotelsByOrganization(organizationId).stream().map(HotelProfile::getId).toList(),"BOOKING_READ");
        PageRange r=pageRange(page,size);
        if(hotelIds!=null&&hotelIds.isEmpty())return PageResponse.of(List.of(),0,Math.max(page,0),r.limit());
        long total=repository.countBookingsByOrganizationFiltered(organizationId,blankToNull(status),hotelIds);
        List<HotelBookingResponse> items=repository.findBookingsByOrganization(organizationId,blankToNull(status),hotelIds,r.limit(),r.offset()).stream().map(this::bookingResponse).toList();
        return PageResponse.of(items,total,Math.max(page,0),r.limit());
    }
    @Override public HotelBookingResponse partnerGetBooking(UUID actor,UUID bookingId){HotelBooking b=bookingRequired(bookingId);authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_READ");return bookingResponse(b);}
    @Override @Transactional public HotelBookingResponse partnerUpdateBookingStatus(UUID actor,UUID bookingId,UpdateHotelBookingStatusRequest request){HotelBooking b=bookingRequired(bookingId);authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_WRITE");requireOperatorTarget(request.getBookingStatus(),false);return transitionBooking(b,request.getBookingStatus(),request.getReason(),request.getExpectedVersion(),request.getGuestCharged(),actor,"USER");}

    @Override public List<HotelProfileResponse> adminListHotels(String q,List<String> status,List<String> propertyType,List<UUID> areas,String sort,boolean descending,int page,int size){PageRange r=pageRange(page,size);return repository.findHotelsAdmin(blankToNull(q),status,propertyType,areas,sort,descending,r.limit(),r.offset()).stream().map(this::hotelResponse).toList();}
    @Override public HotelProfileResponse adminGetHotel(UUID hotelId){return hotelResponse(hotelRequired(hotelId));}
    @Override public List<RoomTypeResponse> adminListRooms(UUID hotelId){hotelRequired(hotelId);return repository.findRoomTypes(hotelId,true).stream().map(this::roomResponse).toList();}
    @Override public List<RatePlanResponse> adminListRates(UUID roomId){roomRequired(roomId);return repository.findRatePlans(roomId,true).stream().map(this::rateResponse).toList();}
    @Override public List<RoomInventoryResponse> adminGetInventory(UUID roomId,LocalDate start,LocalDate end){roomRequired(roomId);validateRange(start,end,730);return repository.findInventory(roomId,start,end).stream().map(this::inventoryResponse).toList();}
    @Override public List<RatePlanDailyRateResponse> adminGetRateCalendar(UUID ratePlanId,LocalDate start,LocalDate end){RatePlan rate=rateRequired(ratePlanId);validateRange(start,end,730);return rateCalendarResponses(rate,start,end);}
    @Override @Transactional public HotelProfileResponse adminUpdateHotelStatus(UUID actor,UUID hotelId,MarketplacePublicationStatus status,String reason,Long expectedVersion){HotelProfile h=hotelRequired(hotelId);h.setStatus(status.name());h.setDisabledReason(status == MarketplacePublicationStatus.ENABLED?null:blankToNull(reason));h.setDataVersion(requiredVersion(expectedVersion));h.setUpdatedAt(LocalDateTime.now());h.setUpdatedBy(actor);optimistic(repository.updateHotel(h),"Hotel");h.setDataVersion(h.getDataVersion()+1);historyService.record(h.getOrganizationId(),"HOTEL",h.getId(),"ADMIN_STATUS_CHANGED",h,List.of("status"),actor,"ADMIN",reason);return hotelResponse(repository.findHotel(hotelId).orElse(h));}
    @Override public List<HotelBookingResponse> adminListBookings(String q,List<String> status,List<String> paymentStatus,String sort,boolean descending,int page,int size){PageRange r=pageRange(page,size);return repository.findBookingsAdmin(blankToNull(q),status,paymentStatus,sort,descending,r.limit(),r.offset()).stream().map(this::bookingResponse).toList();}
    @Override public HotelBookingResponse adminGetBooking(UUID bookingId){return bookingResponse(bookingRequired(bookingId));}
    @Override @Transactional public HotelBookingResponse adminUpdateBookingStatus(UUID actor,UUID bookingId,UpdateHotelBookingStatusRequest request){requireOperatorTarget(request.getBookingStatus(),true);return transitionBooking(bookingRequired(bookingId),request.getBookingStatus(),request.getReason(),request.getExpectedVersion(),request.getGuestCharged(),actor,"ADMIN");}
    @Override @Transactional public HotelProfileResponse adminCreateHotel(UUID actor,UpsertHotelRequest request){return partnerCreateHotel(actor,request);}
    @Override @Transactional public HotelProfileResponse adminUpdateHotel(UUID actor,UUID hotelId,UpsertHotelRequest request){long expected=requiredVersion(request.getExpectedVersion());HotelProfile current=hotelRequired(hotelId);if(MarketplacePublicationStatus.SUSPENDED.name().equals(current.getStatus())){adminUpdateHotelStatus(actor,hotelId,MarketplacePublicationStatus.DISABLED,"Admin editing suspended hotel",expected);request.setExpectedVersion(expected+1);}return partnerUpdateHotel(actor,hotelId,request);}
    @Override @Transactional public RoomTypeResponse adminCreateRoom(UUID actor,UUID hotelId,UpsertRoomTypeRequest request){return partnerCreateRoom(actor,hotelId,request);}
    @Override @Transactional public RoomTypeResponse adminUpdateRoom(UUID actor,UUID roomId,UpsertRoomTypeRequest request){return partnerUpdateRoom(actor,roomId,request);}
    @Override @Transactional public RatePlanResponse adminCreateRate(UUID actor,UUID roomId,UpsertRatePlanRequest request){return partnerCreateRate(actor,roomId,request);}
    @Override @Transactional public RatePlanResponse adminUpdateRate(UUID actor,UUID rateId,UpsertRatePlanRequest request){return partnerUpdateRate(actor,rateId,request);}
    @Override @Transactional public List<RoomInventoryResponse> adminUpdateInventory(UUID actor,UUID roomId,BulkUpdateRoomInventoryRequest request){return partnerUpdateInventory(actor,roomId,request);}
    @Override @Transactional public List<RatePlanDailyRateResponse> adminUpdateRateCalendar(UUID actor,UUID ratePlanId,BulkUpdateRatePlanCalendarRequest request){return partnerUpdateRateCalendar(actor,ratePlanId,request);}

    @Override public List<RatePlanPromotionResponse> partnerListPromotions(UUID actor,UUID hotelId){
        HotelProfile hotel=hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotelId,"HOTEL_READ");
        return promotionRepository.findByHotel(hotelId,false).stream().map(this::promotionResponse).toList();
    }
    @Override @Transactional public RatePlanPromotionResponse partnerCreatePromotion(UUID actor,UUID hotelId,UpsertRatePlanPromotionRequest request){
        HotelProfile hotel=hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotelId,"RATE_WRITE");
        validatePromotion(hotelId,request);
        LocalDateTime now=LocalDateTime.now();
        RatePlanPromotion promotion=RatePlanPromotion.builder().id(UUID.randomUUID()).organizationId(hotel.getOrganizationId()).hotelId(hotelId)
                .ratePlanId(request.getRatePlanId()).code(normalizeCode(request.getCode())).name(request.getName().trim())
                .promotionType(enumNameOrDefault(request.getPromotionType(),"BASIC")).discountPercent(request.getDiscountPercent())
                .priority(request.getPriority()==null?100:request.getPriority()).stayStart(request.getStayStart()).stayEnd(request.getStayEnd())
                .bookStart(request.getBookStart()).bookEnd(request.getBookEnd()).minAdvanceDays(request.getMinAdvanceDays())
                .maxAdvanceDays(request.getMaxAdvanceDays()).minNights(request.getMinNights())
                .daysOfWeek(json(defaultList(request.getDaysOfWeek()).stream().map(Enum::name).toList()))
                .status(enumNameOrDefault(request.getStatus(),MarketplaceAvailabilityStatus.ENABLED)).dataVersion(1L)
                .createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now).build();
        try{promotionRepository.insert(promotion);}catch(DataIntegrityViolationException ex){throw conflict("A promotion with this code already exists for the property");}
        historyService.record(hotel.getOrganizationId(),"RATE_PROMOTION",promotion.getId(),"CREATED",promotion,List.of("PROMOTION"),actor,actorType(actor),null);
        return promotionResponse(promotion);
    }
    @Override @Transactional public RatePlanPromotionResponse partnerUpdatePromotion(UUID actor,UUID hotelId,UUID promotionId,UpsertRatePlanPromotionRequest request){
        HotelProfile hotel=hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotelId,"RATE_WRITE");
        RatePlanPromotion promotion=promotionRepository.findById(promotionId).filter(p->p.getHotelId().equals(hotelId)).orElseThrow(()->notFound("Promotion not found"));
        validatePromotion(hotelId,request);
        promotion.setRatePlanId(request.getRatePlanId());promotion.setCode(normalizeCode(request.getCode()));promotion.setName(request.getName().trim());
        promotion.setPromotionType(enumNameOrDefault(request.getPromotionType(),promotion.getPromotionType()));promotion.setDiscountPercent(request.getDiscountPercent());
        promotion.setPriority(request.getPriority()==null?promotion.getPriority():request.getPriority());
        promotion.setStayStart(request.getStayStart());promotion.setStayEnd(request.getStayEnd());promotion.setBookStart(request.getBookStart());promotion.setBookEnd(request.getBookEnd());
        promotion.setMinAdvanceDays(request.getMinAdvanceDays());promotion.setMaxAdvanceDays(request.getMaxAdvanceDays());promotion.setMinNights(request.getMinNights());
        promotion.setDaysOfWeek(json(defaultList(request.getDaysOfWeek()).stream().map(Enum::name).toList()));
        promotion.setStatus(enumNameOrDefault(request.getStatus(),promotion.getStatus()));
        promotion.setDataVersion(requiredVersion(request.getExpectedVersion()));promotion.setUpdatedBy(actor);promotion.setUpdatedAt(LocalDateTime.now());
        optimistic(promotionRepository.update(promotion),"Promotion");promotion.setDataVersion(promotion.getDataVersion()+1);
        historyService.record(hotel.getOrganizationId(),"RATE_PROMOTION",promotionId,"UPDATED",promotion,List.of("PROMOTION"),actor,actorType(actor),null);
        return promotionResponse(promotion);
    }
    @Override @Transactional public void partnerDeletePromotion(UUID actor,UUID hotelId,UUID promotionId){
        HotelProfile hotel=hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotelId,"RATE_WRITE");
        if(promotionRepository.delete(promotionId,hotelId)!=1)throw notFound("Promotion not found");
        historyService.record(hotel.getOrganizationId(),"RATE_PROMOTION",promotionId,"DELETED",Map.of("id",promotionId.toString()),List.of("PROMOTION"),actor,actorType(actor),null);
    }
    private void validatePromotion(UUID hotelId,UpsertRatePlanPromotionRequest request){
        if(request.getStayStart()!=null&&request.getStayEnd()!=null&&request.getStayEnd().isBefore(request.getStayStart()))throw badRequest("stayEnd must be on or after stayStart");
        if(request.getBookStart()!=null&&request.getBookEnd()!=null&&request.getBookEnd().isBefore(request.getBookStart()))throw badRequest("bookEnd must be on or after bookStart");
        if(request.getMinAdvanceDays()!=null&&request.getMaxAdvanceDays()!=null&&request.getMaxAdvanceDays()<request.getMinAdvanceDays())throw badRequest("maxAdvanceDays must be >= minAdvanceDays");
        if(request.getRatePlanId()!=null){
            RatePlan rate=rateRequired(request.getRatePlanId());
            if(!roomRequired(rate.getRoomTypeId()).getHotelId().equals(hotelId))throw badRequest("The rate plan belongs to another property");
        }
    }
    private RatePlanPromotionResponse promotionResponse(RatePlanPromotion p){
        return RatePlanPromotionResponse.builder().id(p.getId()).organizationId(p.getOrganizationId()).hotelId(p.getHotelId())
                .ratePlanId(p.getRatePlanId()).code(p.getCode()).name(p.getName()).promotionType(p.getPromotionType())
                .discountPercent(p.getDiscountPercent()).priority(p.getPriority()).stayStart(p.getStayStart()).stayEnd(p.getStayEnd())
                .bookStart(p.getBookStart()).bookEnd(p.getBookEnd()).minAdvanceDays(p.getMinAdvanceDays()).maxAdvanceDays(p.getMaxAdvanceDays())
                .minNights(p.getMinNights()).daysOfWeek(readList(p.getDaysOfWeek(),String.class)).status(p.getStatus())
                .dataVersion(p.getDataVersion()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build();
    }
    private List<PromotionEngine.AppliedNight> priceNights(HotelProfile hotel,RatePlan rate,List<HotelAvailabilityDay> days,int quantity,int adults,int children,int nights,LocalDate checkIn,LocalDate today){
        return stayPricer.priceNights(hotel.getId(),rate,days,quantity,adults,children,nights,checkIn,today);
    }
    /** "Today" at the property, not on the server: a Bangkok booking must not be judged by a UTC clock. */
    private LocalDate propertyToday(HotelProfile hotel){
        try { return LocalDate.now(ZoneId.of(hotel.getTimezone())); } catch(Exception ex){ return LocalDate.now(); }
    }
    @Override public ListingReadinessResponse partnerHotelReadiness(UUID actor,UUID hotelId){
        HotelProfile hotel=hotelRequired(hotelId);
        authorizationService.requireResourcePermission(hotel.getOrganizationId(),actor,"HOTEL",hotelId,"HOTEL_READ");
        return ListingReadinessResponse.from(hotelReadiness(hotel));
    }
    /** Booking.com-style open/bookable checklist evaluated against the hotel and its rooms/rates/inventory. */
    private ListingReadiness.Result hotelReadiness(HotelProfile hotel){
        List<RoomType> rooms=repository.findRoomTypes(hotel.getId(),true);
        List<RoomType> enabledRooms=rooms.stream().filter(r->MarketplaceAvailabilityStatus.ENABLED.name().equals(r.getStatus())).toList();
        int photos=(hotel.getPlaceThumbnail()==null?0:1)+readList(hotel.getImages(),String.class).size()+rooms.stream().mapToInt(r->readList(r.getImages(),String.class).size()).sum();
        List<RatePlan> enabledRates=new ArrayList<>();
        for(RoomType room:enabledRooms) repository.findRatePlans(room.getId(),false).stream().filter(r->MarketplaceAvailabilityStatus.ENABLED.name().equals(r.getStatus())).forEach(enabledRates::add);
        boolean pricedRate=enabledRates.stream().anyMatch(r->r.getBasePrice()!=null&&r.getBasePrice().signum()>0);
        boolean policy=enabledRates.stream().anyMatch(r->readMap(r.getCancellationPolicy()).get("type")!=null);
        LocalDate today;try{today=LocalDate.now(ZoneId.of(hotel.getTimezone()));}catch(Exception ex){today=LocalDate.now();}
        final LocalDate start=today;
        boolean inventory=enabledRooms.stream().anyMatch(room->repository.findInventory(room.getId(),start,start.plusDays(29)).stream().anyMatch(i->!Boolean.TRUE.equals(i.getStopSell())&&i.getAvailableUnits()!=null&&i.getAvailableUnits()>0));
        Map<String,Object> contact=readMap(hotel.getBookingContact());
        boolean hasContact=contact.values().stream().anyMatch(v->v!=null&&!String.valueOf(v).isBlank());
        String description=hotel.getDescription()==null?"":hotel.getDescription().trim();
        List<ListingReadiness.Check> checks=List.of(
            ListingReadiness.of("PHOTOS_MIN_5","At least 5 photos",photos>=5,false,10,photos+" photo(s)"),
            ListingReadiness.of("DESCRIPTION","Description of at least 80 characters",description.length()>=80,false,10,description.length()+" characters"),
            ListingReadiness.of("CHECKIN_TIMES","Check-in and check-out times",hotel.getCheckInTime()!=null&&hotel.getCheckOutTime()!=null,true,10,null),
            ListingReadiness.of("CONTACT","Booking contact (phone or email)",hasContact,true,10,null),
            ListingReadiness.of("ROOM_ENABLED","At least one enabled room type",!enabledRooms.isEmpty(),true,15,enabledRooms.size()+" enabled"),
            ListingReadiness.of("RATE_ENABLED","At least one enabled rate plan",!enabledRates.isEmpty(),true,15,enabledRates.size()+" enabled"),
            ListingReadiness.of("RATE_HAS_PRICE_30D","A rate plan with a price",pricedRate,true,10,null),
            ListingReadiness.of("INVENTORY_30D","Rooms available in the next 30 days",inventory,true,10,null),
            ListingReadiness.of("CANCELLATION_POLICY","Cancellation policy on an enabled rate",policy,true,5,null),
            ListingReadiness.of("AMENITIES","At least 3 amenities",readList(hotel.getAmenities(),String.class).size()>=3,false,5,null));
        return ListingReadiness.score(checks);
    }

    @Override public StayQuote quoteStayChange(UUID bookingId,LocalDate checkIn,LocalDate checkOut,Integer adults,Integer children){
        HotelBooking b=bookingRequired(bookingId);
        HotelBookingItem item=repository.findBookingItems(b.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));
        try{
            RoomType room=roomRequired(item.getRoomTypeId());RatePlan rate=rateRequired(item.getRatePlanId());HotelProfile hotel=hotelRequired(b.getHotelId());
            BigDecimal total=priceStay(hotel,room,rate,checkIn,checkOut,item.getQuantity(),adults==null?b.getAdults():adults,children==null?b.getChildren():children,b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity());
            return new StayQuote(true,null,total,rate.getCurrency(),Math.toIntExact(ChronoUnit.DAYS.between(checkIn,checkOut)));
        }catch(BusinessException ex){return new StayQuote(false,ex.getMessage(),null,b.getCurrency(),0);}
    }

    @Override @Transactional public HotelBookingResponse partnerApplyStayChange(UUID actor,UUID bookingId,LocalDate checkIn,LocalDate checkOut,Integer adults,Integer children,Long expectedVersion){
        HotelBooking b=bookingRequired(bookingId);
        authorizationService.requireResourcePermission(b.getOrganizationId(),actor,"HOTEL",b.getHotelId(),"BOOKING_WRITE");
        MarketplaceBookingStatus status;try{status=MarketplaceBookingStatus.valueOf(b.getBookingStatus());}catch(IllegalArgumentException ex){throw badRequest("Invalid stored booking status");}
        if(status!=MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION&&status!=MarketplaceBookingStatus.CONFIRMED)throw badRequest("Only pending or confirmed bookings can be changed");
        validateStay(checkIn,checkOut);
        HotelBookingItem item=repository.findBookingItems(b.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));
        RoomType room=roomRequired(item.getRoomTypeId());RatePlan rate=rateRequired(item.getRatePlanId());HotelProfile hotel=hotelRequired(b.getHotelId());
        int quantity=item.getQuantity();int newAdults=adults==null?b.getAdults():adults;int newChildren=children==null?b.getChildren():children;
        LocalDateTime now=LocalDateTime.now();boolean reserved=status==MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION;
        int oldNights=Math.toIntExact(ChronoUnit.DAYS.between(b.getCheckInDate(),b.getCheckOutDate()));
        // Give the old nights back first so an overlapping new range can reuse them; the transaction undoes it on any failure.
        if(repository.releaseInventory(room.getId(),b.getCheckInDate(),b.getCheckOutDate(),quantity,reserved,actor,now)!=oldNights)throw conflict("Allocated inventory is inconsistent");
        BigDecimal total=priceStay(hotel,room,rate,checkIn,checkOut,quantity,newAdults,newChildren,null,null,quantity);
        int newNights=Math.toIntExact(ChronoUnit.DAYS.between(checkIn,checkOut));
        if(repository.reserveInventory(room.getId(),rate.getId(),checkIn,checkOut,quantity,actor,now)!=newNights)throw conflict("The requested dates are no longer available");
        if(!reserved&&repository.confirmReservedInventory(room.getId(),checkIn,checkOut,quantity,actor,now)!=newNights)throw conflict("Reserved inventory is inconsistent");
        Map<String,Object> snapshot=new LinkedHashMap<>(readMap(b.getSnapshot()));
        @SuppressWarnings("unchecked") List<Object> changes=snapshot.get("changeHistory") instanceof List<?> l?new ArrayList<>((List<Object>)l):new ArrayList<>();
        changes.add(Map.of("at",now.toString(),"fromCheckIn",b.getCheckInDate().toString(),"fromCheckOut",b.getCheckOutDate().toString(),"toCheckIn",checkIn.toString(),"toCheckOut",checkOut.toString(),"priceBefore",b.getTotalAmount(),"priceAfter",total,"by",actor.toString()));
        snapshot.put("changeHistory",changes);
        optimistic(repository.updateBookingStay(b.getId(),requiredVersion(expectedVersion),checkIn,checkOut,newAdults,newChildren,total,total,json(snapshot),actor,now),"Booking");
        repository.updateBookingItemStay(item.getId(),newAdults,newChildren,total.divide(BigDecimal.valueOf(quantity),2,RoundingMode.HALF_UP),total);
        HotelBooking saved=bookingRequired(b.getId());
        historyService.record(saved.getOrganizationId(),"HOTEL_BOOKING",saved.getId(),"STAY_CHANGED",saved,List.of("checkInDate","checkOutDate","adults","children","totalAmount"),actor,"USER",null);
        return bookingResponse(saved);
    }

    /**
     * Prices a stay against the live calendar with the same rules as createBooking. When the booking already holds
     * {@code ownedNights} in the range (a change that overlaps the current stay) those units are counted as available.
     */
    private BigDecimal priceStay(HotelProfile hotel,RoomType room,RatePlan rate,LocalDate in,LocalDate out,int quantity,int adults,int children,LocalDate ownedIn,LocalDate ownedOut,int ownedQuantity){
        validateStay(in,out,propertyToday(hotel));
        if(adults>room.getMaxAdults()*quantity||children>room.getMaxChildren()*quantity||adults+children>room.getMaxOccupancy()*quantity)throw badRequest("Guest count exceeds room capacity");
        LocalDate today=propertyToday(hotel);
        List<HotelAvailabilityDay> days=repository.findAvailability(hotel.getId(),room.getId(),rate.getId(),in,out,today);
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(in,out));
        if(days.size()!=nights)throw conflict("Selected room is not open on every night");
        for(HotelAvailabilityDay d:days){
            boolean owned=ownedIn!=null&&!d.getInventoryDate().isBefore(ownedIn)&&d.getInventoryDate().isBefore(ownedOut);
            int available=(d.getAvailableUnits()==null?0:d.getAvailableUnits())+(owned?ownedQuantity:0);
            if(Boolean.TRUE.equals(d.getStopSell())||available<quantity)throw conflict("Selected room is not available on "+d.getInventoryDate());
        }
        if(Boolean.TRUE.equals(days.get(0).getClosedToArrival()))throw conflict("Arrival is closed for selected date");
        if(Boolean.TRUE.equals(days.get(nights-1).getClosedToDeparture()))throw conflict("Departure is closed for selected date");
        int minimum=days.stream().map(HotelAvailabilityDay::getMinStay).filter(Objects::nonNull).max(Integer::compareTo).orElse(1);
        int maximum=days.stream().map(HotelAvailabilityDay::getMaxStay).filter(Objects::nonNull).min(Integer::compareTo).orElse(Integer.MAX_VALUE);
        if(nights<minimum||nights>maximum)throw conflict("Stay length is outside rate plan limits");
        return priceNights(hotel,rate,days,quantity,adults,children,nights,in,today).stream()
                .map(PromotionEngine.AppliedNight::price).reduce(BigDecimal.ZERO,BigDecimal::add).setScale(2,RoundingMode.HALF_UP);
    }

    private HotelBookingResponse transitionBooking(HotelBooking b,MarketplaceBookingStatus target,String reason,Long requestedVersion,Boolean guestCharged,UUID actor,String actorType){
        validateTransition(b.getBookingStatus(),target);validatePaymentCompatibility(b.getPaymentStatus(),target);if(target==MarketplaceBookingStatus.NO_SHOW) validateNoShowCutoff(b);HotelBookingItem item=repository.findBookingItems(b.getId()).stream().findFirst().orElseThrow(()->notFound("Booking item not found"));
        int nights=Math.toIntExact(ChronoUnit.DAYS.between(b.getCheckInDate(),b.getCheckOutDate()));LocalDateTime now=LocalDateTime.now();
        if(target.confirmsReservedInventory()){int n=repository.confirmReservedInventory(item.getRoomTypeId(),b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity(),actor,now);if(n!=nights)throw conflict("Reserved inventory is inconsistent");}
        else if(target.releasesInventory()){boolean reserved=MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name().equals(b.getBookingStatus());int n=repository.releaseInventory(item.getRoomTypeId(),b.getCheckInDate(),b.getCheckOutDate(),item.getQuantity(),reserved,actor,now);if(n!=nights)throw conflict("Allocated inventory is inconsistent");}
        long version=requiredVersion(requestedVersion);LocalDateTime cancelled=target.releasesInventory()?now:null;
        optimistic(repository.updateBookingStatus(b.getId(),version,target.name(),null,blankToNull(reason),target==MarketplaceBookingStatus.NO_SHOW?guestCharged:null,cancelled,actor,now),"Booking");
        HotelBooking saved=bookingRequired(b.getId());historyService.record(saved.getOrganizationId(),"HOTEL_BOOKING",saved.getId(),"STATUS_CHANGED",saved,List.of("bookingStatus"),actor,actorType,reason);
        if(target==MarketplaceBookingStatus.CONFIRMED||target==MarketplaceBookingStatus.CANCELLED_BY_HOST) notifyGuestStatus(saved,target,reason,actor);
        else if(target==MarketplaceBookingStatus.CANCELLED_BY_GUEST) notifyPartnerGuestCancelled(saved,actor);
        else notifyGuestLifecycle(saved,target,reason,actor);
        if(target==MarketplaceBookingStatus.COMPLETED) notifyReviewInvite(saved,actor);
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
        LocalDateTime now=LocalDateTime.now(timezone);
        LocalDateTime windowStart=booking.getCheckInDate().atStartOfDay();
        LocalDateTime windowEnd=booking.getCheckOutDate().plusDays(1).atStartOfDay().plusHours(NO_SHOW_WINDOW_HOURS);
        if(now.isBefore(windowStart))throw badRequest("A booking can be marked no-show only from the check-in date");
        if(now.isAfter(windowEnd))throw badRequest("The no-show window closed 48 hours after check-out; contact support");
    }
    private int daysUntilCheckIn(HotelProfile hotel, LocalDate checkIn){
        try{return Math.toIntExact(ChronoUnit.DAYS.between(LocalDate.now(ZoneId.of(hotel.getTimezone())),checkIn));}
        catch(Exception ex){throw badRequest("Hotel timezone is invalid; cannot validate booking window");}
    }
    private String inventoryExpectedVersionsJson(UUID roomId,LocalDate start,LocalDate end){Map<LocalDate,Long> versions=new LinkedHashMap<>();repository.findInventory(roomId,start,end).forEach(row->versions.put(row.getInventoryDate(),row.getDataVersion()));start.datesUntil(end.plusDays(1)).forEach(day->versions.putIfAbsent(day,0L));return json(versions);}
    private void requireOperatorTarget(MarketplaceBookingStatus target,boolean admin){
        if(target==null)throw badRequest("bookingStatus is required");
        if(admin?target.canBeSetByAdmin():target.canBeSetByPartner())return;
        if(target==MarketplaceBookingStatus.CANCELLED_BY_GUEST)throw badRequest("Only the guest can set CANCELLED_BY_GUEST");
        if(target.isSystemOnly())throw badRequest("Status "+target+" is set by the system only");
        throw badRequest("Status "+target+" can only be set by the platform");
    }
    private HotelBookingResponse bookingResponse(HotelBooking b){List<HotelBookingItemResponse> items=repository.findBookingItems(b.getId()).stream().map(i->HotelBookingItemResponse.builder().id(i.getId()).roomTypeId(i.getRoomTypeId()).ratePlanId(i.getRatePlanId()).roomTypeName(i.getRoomTypeName()).ratePlanName(i.getRatePlanName()).quantity(i.getQuantity()).adults(i.getAdults()).children(i.getChildren()).unitPrice(i.getUnitPrice()).totalPrice(i.getTotalPrice()).snapshot(readMap(i.getSnapshot())).build()).toList();return HotelBookingResponse.builder().id(b.getId()).bookingCode(b.getBookingCode()).userId(b.getUserId()).organizationId(b.getOrganizationId()).hotelId(b.getHotelId()).hotelName(b.getHotelName()).checkInDate(b.getCheckInDate()).checkOutDate(b.getCheckOutDate()).adults(b.getAdults()).children(b.getChildren()).guestLead(readMap(b.getGuestLead())).guestDetails(readValue(b.getGuestDetails(),new TypeReference<List<Map<String,Object>>>(){},List.of())).specialRequests(b.getSpecialRequests()).estimatedArrivalTime(b.getEstimatedArrivalTime()).holdExpiresAt(b.getHoldExpiresAt()).guestCharged(b.getGuestCharged()).currency(b.getCurrency()).subtotalAmount(b.getSubtotalAmount()).taxAmount(b.getTaxAmount()).feeAmount(b.getFeeAmount()).discountAmount(b.getDiscountAmount()).totalAmount(b.getTotalAmount()).bookingStatus(b.getBookingStatus()).paymentStatus(b.getPaymentStatus()).source(b.getSource()).cancellationReason(b.getCancellationReason()).cancelledAt(b.getCancelledAt()).snapshot(readMap(b.getSnapshot())).dataVersion(b.getDataVersion()).items(items).createdAt(b.getCreatedAt()).updatedAt(b.getUpdatedAt()).build();}
    private HotelProfileResponse hotelResponse(HotelProfile h){return catalogAssembler.toResponse(h);}
    private com.ds.goroute.dto.HotelPolicies policiesOf(UpsertHotelRequest r){return r.getPolicies()==null?new com.ds.goroute.dto.HotelPolicies():r.getPolicies();}
    private void validateOverviewYears(UpsertHotelRequest r){if(r.getOpenedYear()!=null&&r.getRenovatedYear()!=null&&r.getRenovatedYear()<r.getOpenedYear())throw badRequest("renovatedYear must not be before openedYear");}
    private RoomTypeResponse roomResponse(RoomType r){return catalogAssembler.toRoomResponse(r);}
    private RatePlanResponse rateResponse(RatePlan r){return catalogAssembler.toRateResponse(r);}
    private RoomInventoryResponse inventoryResponse(RoomInventoryDaily i){return RoomInventoryResponse.builder().roomTypeId(i.getRoomTypeId()).inventoryDate(i.getInventoryDate()).totalUnits(i.getTotalUnits()).reservedUnits(i.getReservedUnits()).soldUnits(i.getSoldUnits()).blockedUnits(i.getBlockedUnits()).availableUnits(i.getAvailableUnits()).stopSell(i.getStopSell()).priceOverride(i.getPriceOverride()).minStay(i.getMinStay()).closedToArrival(i.getClosedToArrival()).closedToDeparture(i.getClosedToDeparture()).dataVersion(i.getDataVersion()).updatedAt(i.getUpdatedAt()).build();}
    private List<RatePlanDailyRateResponse> rateCalendarResponses(RatePlan rate,LocalDate start,LocalDate end){Map<LocalDate,RatePlanDailyRate> overrides=repository.findRatePlanDailyRates(rate.getId(),start,end).stream().collect(java.util.stream.Collectors.toMap(RatePlanDailyRate::getRateDate,value->value));return start.datesUntil(end.plusDays(1)).map(day->{RatePlanDailyRate value=overrides.get(day);return RatePlanDailyRateResponse.builder().ratePlanId(rate.getId()).rateDate(day).price(value==null?null:value.getPrice()).effectivePrice(value!=null&&value.getPrice()!=null?value.getPrice():rate.getBasePrice()).stopSell(value!=null&&Boolean.TRUE.equals(value.getStopSell())).minStay(value!=null&&value.getMinStay()!=null?value.getMinStay():rate.getMinStay()).maxStay(value!=null&&value.getMaxStay()!=null?value.getMaxStay():rate.getMaxStay()).closedToArrival(value!=null&&Boolean.TRUE.equals(value.getClosedToArrival())).closedToDeparture(value!=null&&Boolean.TRUE.equals(value.getClosedToDeparture())).minAdvanceDays(value!=null&&value.getMinAdvanceDays()!=null?value.getMinAdvanceDays():rate.getMinAdvanceDays()).maxAdvanceDays(value!=null&&value.getMaxAdvanceDays()!=null?value.getMaxAdvanceDays():rate.getMaxAdvanceDays()).dataVersion(value==null?null:value.getDataVersion()).updatedAt(value==null?null:value.getUpdatedAt()).build();}).toList();}

    /**
     * Public catalogue prices are shown in the guest's currency. Only the money
     * fields move: policy JSON stays as the partner authored it, and everything
     * reached through a partner or admin endpoint keeps the stored price.
     */
    private HotelProfileResponse displayed(HotelProfileResponse h){if(h.getFromPrice()==null)return h;h.setFromPrice(displayPrice.convert(h.getFromPrice(),h.getFromPriceCurrency()));h.setFromPriceCurrency(displayPrice.currencyOf(h.getFromPriceCurrency()));return h;}
    private RatePlanResponse displayed(RatePlanResponse r){return catalogAssembler.displayed(r);}

    /** The rate plan's advance-booking window, as createBooking enforces it on the arrival night. */
    private void requireWithinBookingWindow(HotelProfile hotel,RoomType room,RatePlan rate,LocalDate checkIn,LocalDate checkOut){
        LocalDate today=propertyToday(hotel);
        List<HotelAvailabilityDay> days=repository.findAvailability(hotel.getId(),room.getId(),rate.getId(),checkIn,checkOut,today);
        if(days.isEmpty())throw conflict("Selected room is no longer available");
        int advanceDays=Math.toIntExact(ChronoUnit.DAYS.between(today,checkIn));
        int minimumAdvance=Optional.ofNullable(days.get(0).getMinAdvanceDays()).orElse(0);
        Integer maximumAdvance=days.get(0).getMaxAdvanceDays();
        if(advanceDays<minimumAdvance||(maximumAdvance!=null&&advanceDays>maximumAdvance))throw badRequest("Check-in date is outside the rate plan booking window");
    }

    private HotelProfile hotelRequired(UUID id){return repository.findHotel(id).orElseThrow(()->notFound("Hotel not found"));}
    private HotelProfile publicHotelRequired(UUID id){return repository.findPublicHotel(id).orElseThrow(()->notFound("Hotel not found"));}
    private RoomType roomRequired(UUID id){return repository.findRoomType(id).orElseThrow(()->notFound("Room type not found"));}
    private RatePlan rateRequired(UUID id){return repository.findRatePlan(id).orElseThrow(()->notFound("Rate plan not found"));}
    private RatePlan rateRequiredForBooking(UUID id){return repository.findRatePlanForBooking(id).orElseThrow(()->notFound("Rate plan not found"));}
    private HotelBooking bookingRequired(UUID id){return repository.findBooking(id).orElseThrow(()->notFound("Hotel booking not found"));}
    private HotelProfile hotelForRoom(UUID roomId){return hotelRequired(roomRequired(roomId).getHotelId());}
    private boolean organizationBookable(UUID organizationId){return organizationRepository.findById(organizationId)
            .map(org->OrganizationOperationalStatus.ENABLED.name().equals(org.getOperationalStatus())&&OrganizationVerificationStatus.VERIFIED.name().equals(org.getVerificationStatus()))
            .orElse(false);}
    private void validateStay(LocalDate in,LocalDate out){validateStay(in,out,LocalDate.now());}
    private void validateStay(LocalDate in,LocalDate out,LocalDate today){if(in==null||out==null||!out.isAfter(in)||in.isBefore(today))throw badRequest("Invalid check-in/check-out dates");long nights=ChronoUnit.DAYS.between(in,out);if(nights>MAX_BOOKING_NIGHTS)throw badRequest("Maximum stay is "+MAX_BOOKING_NIGHTS+" nights");}
    private void validateRange(LocalDate start,LocalDate end,int max){if(start==null||end==null||end.isBefore(start)||ChronoUnit.DAYS.between(start,end)+1>max)throw badRequest("Invalid date range");}
    private void validateRoom(UpsertRoomTypeRequest r){if(r.getMaxOccupancy()<r.getMaxAdults()||r.getMaxOccupancy()<1)throw badRequest("maxOccupancy must cover adults");if(r.getStandardAdults()>r.getMaxAdults())throw badRequest("standardAdults must be <= maxAdults");}
    private void validateRate(UpsertRatePlanRequest r){if(r.getPricingModel()!=null&&"DERIVED".equals(r.getPricingModel().name()))throw badRequest("Derived pricing is not supported yet; use STANDARD, OCCUPANCY_BASED or LENGTH_OF_STAY");if(r.getMaxStay()!=null&&r.getMaxStay()<r.getMinStay())throw badRequest("maxStay must be >= minStay");if(r.getMaxAdvanceDays()!=null&&r.getMinAdvanceDays()!=null&&r.getMaxAdvanceDays()<r.getMinAdvanceDays())throw badRequest("maxAdvanceDays must be >= minAdvanceDays");try{Currency.getInstance(r.getCurrency());}catch(Exception ex){throw badRequest("Invalid currency");}}
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
    private void notifyPartnerRequest(UUID organizationId, UUID hotelId, UUID bookingId, String code, String kind, LocalDateTime deadline, UUID guestId){
        Map<String,Object> data=Map.of("bookingId", bookingId.toString(), "bookingCode", code, "deepLink", "/partner/bookings?booking=" + bookingId);
        authorizationService.notificationRecipients(organizationId,"HOTEL",hotelId,"BOOKING_READ",guestId).forEach(userId -> notificationService.createNotification(userId, null,
                NotificationType.MARKETPLACE_BOOKING_REQUEST, "New " + kind + " request", code + " needs a response before " + deadline, data, guestId));
    }
    private void notifyPartnerGuestCancelled(HotelBooking booking, UUID guestId){
        Map<String,Object> data=Map.of("bookingId", booking.getId().toString(), "bookingCode", booking.getBookingCode(), "deepLink", "/partner/bookings?booking=" + booking.getId());
        authorizationService.notificationRecipients(booking.getOrganizationId(),"HOTEL",booking.getHotelId(),"BOOKING_READ",guestId).forEach(userId -> notificationService.createNotification(userId, null,
                NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST, "Guest cancelled a booking", booking.getBookingCode() + " was cancelled by the guest", data, guestId));
    }
    private void notifyReviewInvite(HotelBooking booking, UUID actor){
        Map<String,Object> data=Map.of("bookingId", booking.getId().toString(), "hotelBookingId", booking.getId().toString(), "bookingCode", booking.getBookingCode(), "deepLink", "/marketplace/orders/hotel/" + booking.getId());
        notificationService.createNotification(booking.getUserId(), null, NotificationType.MARKETPLACE_REVIEW_INVITE, "How was your stay?", booking.getBookingCode() + " is complete — share a review", data, actor);
    }
    private void notifyGuestLifecycle(HotelBooking booking, MarketplaceBookingStatus target, String reason, UUID actor){
        NotificationType type=target==MarketplaceBookingStatus.EXPIRED?NotificationType.MARKETPLACE_BOOKING_EXPIRED:NotificationType.MARKETPLACE_BOOKING_UPDATED;
        String label=target.name().toLowerCase(Locale.ROOT).replace('_',' ');
        Map<String,Object> data=Map.of("bookingId", booking.getId().toString(), "bookingCode", booking.getBookingCode(), "statusLabel", label, "status", target.name(), "deepLink", "/marketplace/orders");
        notificationService.createNotification(booking.getUserId(), null, type, "Booking " + label, booking.getBookingCode() + (reason==null?"":": "+reason), data, actor);
    }
    private void notifyGuestStatus(HotelBooking booking, MarketplaceBookingStatus target, String reason, UUID actor){
        notificationService.createNotification(booking.getUserId(), null, target==MarketplaceBookingStatus.CONFIRMED?NotificationType.MARKETPLACE_BOOKING_CONFIRMED:NotificationType.MARKETPLACE_BOOKING_DECLINED,
                target==MarketplaceBookingStatus.CONFIRMED?"Booking confirmed":"Booking request declined", booking.getBookingCode() + (reason==null?"":": "+reason),
                Map.of("bookingId", booking.getId().toString(), "deepLink", "/marketplace/orders"), actor);
    }
    private record PageRange(int limit,int offset){}
}
