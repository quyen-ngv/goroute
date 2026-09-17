package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.GeoCoordinateDto;
import com.ds.goroute.dto.ActivityPackageUnit;
import com.ds.goroute.dto.request.*;
import com.ds.goroute.dto.response.*;
import com.ds.goroute.entity.*;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ActivityCommerceRepository;
import com.ds.goroute.repository.HostOrganizationRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.mapper.AdminMapper;
import com.ds.goroute.service.ActivityCommerceService;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.NotificationService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.marketplace.CancellationPolicyEvaluator;
import com.ds.goroute.service.marketplace.ListingReadiness;
import com.ds.goroute.type.MarketplaceAvailabilityStatus;
import com.ds.goroute.type.ActivityInventoryType;
import com.ds.goroute.type.MarketplaceBookingStatus;
import com.ds.goroute.type.MarketplaceConfirmationType;
import com.ds.goroute.type.MarketplacePaymentStatus;
import com.ds.goroute.type.MarketplacePublicationStatus;
import com.ds.goroute.type.MarketplaceSlotStatus;
import com.ds.goroute.type.OrganizationOperationalStatus;
import com.ds.goroute.type.OrganizationVerificationStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service @RequiredArgsConstructor
public class ActivityCommerceServiceImpl implements ActivityCommerceService {
 private final ActivityCommerceRepository repository;private final PlaceRepository placeRepository;private final HostOrganizationRepository organizationRepository;private final PartnerAuthorizationService authorization;
 private final MarketplaceHistoryService history;private final com.ds.goroute.service.MarketplaceCommissionService commissionService;private final ObjectMapper objectMapper;
 private final NotificationService notificationService;
 private final AdminMapper adminMapper;
 private final com.ds.goroute.service.marketplace.MarketplaceDisplayPrice displayPrice;
 @Value("${goroute.marketplace.partner-confirmation-sla-minutes:120}") private long partnerConfirmationSlaMinutes=120;

 public List<MarketplaceActivityResponse> listPublic(String q,String kind,int p,int s){Page r=page(p,s);return repository.findProductsPublic(clean(q),activityTypesOfKind(kind),r.limit,r.offset).stream().map(this::productResponse).map(this::displayed).toList();}
  public List<MarketplaceActivityResponse> listSimilarPublic(UUID id,int size){
   MarketplaceActivityProduct p=publicProduct(id);
   List<String> types=activityTypesOfKind(com.ds.goroute.type.ActivityProductType.kindOf(p.getActivityType()));
   return repository.findSimilarProductsPublic(id,types,p.getSearchLat(),p.getSearchLng(),Math.min(Math.max(size,1),20)).stream().map(this::productResponse).map(this::displayed).toList();
  }
  /** Activity types that belong to a detail-page kind; null or blank kind means every type. */
  private List<String> activityTypesOfKind(String kind){
   String k=clean(kind);if(k==null)return null;
   boolean ticket;switch(k.toUpperCase(Locale.ROOT)){case "TICKET"->ticket=true;case "TOUR"->ticket=false;default->throw bad("kind must be TOUR or TICKET");}
   return Arrays.stream(com.ds.goroute.type.ActivityProductType.values()).filter(t->t.isTicket()==ticket).map(Enum::name).toList();
  }
  /**
   * Groups the open slots of every enabled package by local date. A slot counts when it is
   * ENABLED, still before its booking cutoff and has capacity left; the day price of a
   * package is its cheapest unit price on that day, with the same precedence createOrder
   * uses (slot unit price, slot override, unit price, package price).
   */
  public List<ActivityAvailabilityDayResponse> availabilityPublic(UUID id,LocalDate from,int days){
   getPublic(id);
   int span=Math.min(Math.max(days,1),120);
   LocalDate start=from==null?LocalDate.now():from;LocalDate end=start.plusDays(span);
   Map<LocalDate,Map<UUID,ActivityAvailabilityDayResponse.PackageDay>> byDate=new TreeMap<>();
   String currency=null;
   for(ActivityPackage a:repository.findPackages(id,false)){
    List<ActivityPackageUnit> units=readList(a.getUnits(),ActivityPackageUnit.class);
    for(ActivitySlot s:repository.findSlots(a.getId(),start.atStartOfDay(),false)){
     LocalDate date=s.getStartsAt().toLocalDate();
     if(!date.isBefore(end))break;
     int cutoff=s.getBookingCutoffMinutes()==null?0:s.getBookingCutoffMinutes();
     int available=s.getAvailableQuantity()==null?0:s.getAvailableQuantity();
     if(available<=0||s.getStartsAt().minusMinutes(cutoff).isBefore(slotNow(s)))continue;
     BigDecimal price=cheapestSlotPrice(a,s,units);
     currency=displayPrice.currencyOf(a.getCurrency());
     byDate.computeIfAbsent(date,x->new LinkedHashMap<>()).merge(a.getId(),
      ActivityAvailabilityDayResponse.PackageDay.builder().packageId(a.getId()).fromPrice(displayPrice.convert(price,a.getCurrency())).originalPrice(displayPrice.convert(a.getOriginalPrice(),a.getCurrency())).availableQuantity(available).slotCount(1).build(),
      (x,y)->{x.setFromPrice(x.getFromPrice().min(y.getFromPrice()));x.setAvailableQuantity(x.getAvailableQuantity()+y.getAvailableQuantity());x.setSlotCount(x.getSlotCount()+1);return x;});
    }
   }
   String dayCurrency=currency;
   return byDate.entrySet().stream().map(e->{List<ActivityAvailabilityDayResponse.PackageDay> packages=new ArrayList<>(e.getValue().values());
    return ActivityAvailabilityDayResponse.builder().date(e.getKey()).currency(dayCurrency).packages(packages).fromPrice(packages.stream().map(ActivityAvailabilityDayResponse.PackageDay::getFromPrice).min(BigDecimal::compareTo).orElse(null)).build();}).toList();
  }
  /** Cheapest non-free unit price of a slot, so "from" never reads 0 because of an infant ticket. */
  private BigDecimal cheapestSlotPrice(ActivityPackage a,ActivitySlot s,List<ActivityPackageUnit> units){
   if(units.isEmpty())return s.getPriceOverride()==null?a.getBasePrice():s.getPriceOverride();
   Map<String,BigDecimal> slotPrices=new HashMap<>();readValue(s.getUnitPrices(),new TypeReference<Map<String,BigDecimal>>(){},Map.<String,BigDecimal>of()).forEach((c,v)->slotPrices.put(c.trim().toUpperCase(Locale.ROOT),v));
   BigDecimal best=null;
   for(ActivityPackageUnit u:units){BigDecimal price=slotPrices.get(u.getCode().trim().toUpperCase(Locale.ROOT));if(price==null)price=s.getPriceOverride();if(price==null)price=u.getPrice();if(price==null)price=a.getBasePrice();
    if(price.signum()>0&&(best==null||price.compareTo(best)<0))best=price;}
   return best==null?a.getBasePrice():best;
  }
 public MarketplaceActivityResponse getPublic(UUID id){return displayed(productResponse(publicProduct(id)));}
 public List<ActivityPackageResponse> listPublicPackages(UUID id){getPublic(id);return repository.findPackages(id,false).stream().map(this::packageResponse).map(this::displayed).toList();}
 public List<ActivitySlotResponse> listPublicSlots(UUID id,LocalDateTime from){ActivityPackage p=pack(id);getPublic(p.getActivityBookingId());if(!MarketplaceAvailabilityStatus.ENABLED.name().equals(p.getStatus()))throw notFound("Package not found");return repository.findSlots(id,from==null?LocalDateTime.now():from,false).stream().map(this::slotResponse).map(slot->displayed(slot,p.getCurrency())).toList();}
 public List<MarketplaceActivityResponse> partnerList(UUID actor,UUID org){authorization.requireOrganization(org,actor);return repository.findProductsByOrganization(org).stream().filter(p->authorization.hasResourcePermission(org,actor,"ACTIVITY",p.getId(),"ACTIVITY_READ")).map(this::productResponse).toList();}

 @Transactional public MarketplaceActivityResponse partnerCreate(UUID actor,UpsertMarketplaceActivityRequest r){authorization.requirePermission(r.getOrganizationId(),actor,"ACTIVITY_WRITE");if(r.getPlaceId()!=null)placeRepository.findById(r.getPlaceId()).orElseThrow(()->new BusinessException(ErrorConstant.PLACE_NOT_FOUND));currency(r.getPriceCurrency());LocalDateTime now=LocalDateTime.now();MarketplaceActivityProduct p=MarketplaceActivityProduct.builder().id(UUID.randomUUID()).organizationId(r.getOrganizationId()).placeId(r.getPlaceId()).source("GOROUTE").productStatus(partnerPublicationStatus(r.getProductStatus(),MarketplacePublicationStatus.DRAFT.name())).inventoryMode("INTERNAL").dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(now).updatedAt(now).build();applyProductDetails(p,r);repository.insertProduct(p);history.record(p.getOrganizationId(),"ACTIVITY_PRODUCT",p.getId(),"CREATED",p,List.of(),actor,actorType(actor),null);return productResponse(repository.findProduct(p.getId()).orElse(p));}
 @Transactional public MarketplaceActivityResponse partnerUpdate(UUID actor,UUID id,UpsertMarketplaceActivityRequest r){MarketplaceActivityProduct p=product(id);authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"ACTIVITY_WRITE");if(MarketplacePublicationStatus.SUSPENDED.name().equals(p.getProductStatus()))throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,"A suspended activity can only be changed by an admin");if(!p.getOrganizationId().equals(r.getOrganizationId())||!Objects.equals(p.getPlaceId(),r.getPlaceId()))throw bad("organizationId and placeId are immutable");currency(r.getPriceCurrency());long v=requiredVersion(r.getExpectedVersion());applyProductDetails(p,r);String nextStatus=partnerPublicationStatus(r.getProductStatus(),p.getProductStatus());if(MarketplacePublicationStatus.ENABLED.name().equals(nextStatus)&&!MarketplacePublicationStatus.ENABLED.name().equals(p.getProductStatus())){ListingReadiness.Result readiness=productReadiness(p);if(!readiness.ready())throw bad("The listing is not ready to sell yet: "+String.join(", ",readiness.failingRequiredCodes()));}p.setProductStatus(nextStatus);p.setDataVersion(v);p.setUpdatedBy(actor);p.setUpdatedAt(LocalDateTime.now());optimistic(repository.updateProduct(p),"Activity");p.setDataVersion(v+1);history.record(p.getOrganizationId(),"ACTIVITY_PRODUCT",id,"UPDATED",p,List.of("PROFILE","LOCATION","ITINERARY","MEDIA"),actor,"USER",null);return productResponse(repository.findProduct(id).orElse(p));}
 public List<ActivityPackageResponse> partnerPackages(UUID actor,UUID id){MarketplaceActivityProduct p=product(id);authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"ACTIVITY_READ");return repository.findPackages(id,true).stream().map(this::packageResponse).toList();}

 @Transactional public ActivityPackageResponse partnerCreatePackage(UUID actor,UUID id,UpsertActivityPackageRequest r){MarketplaceActivityProduct p=product(id);authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"ACTIVITY_WRITE");validatePackage(r);LocalDateTime n=LocalDateTime.now();ActivityPackage v=ActivityPackage.builder().id(UUID.randomUUID()).activityBookingId(id).code(code(r.getCode())).name(r.getName().trim()).description(clean(r.getDescription())).currency(r.getCurrency().toUpperCase()).basePrice(r.getBasePrice()).originalPrice(r.getOriginalPrice()).packageGroup(clean(r.getPackageGroup())).groupType(r.getGroupType()==null?null:r.getGroupType().name()).departureType(r.getDepartureType()==null?null:r.getDepartureType().name()).details(json(r.getDetails()==null?Map.of():r.getDetails())).minQuantity(r.getMinQuantity()).maxQuantity(r.getMaxQuantity()).inventoryType(enumNameOrDefault(r.getInventoryType(),"SLOT")).units(json(list(r.getUnits()))).includedItems(json(list(r.getIncludedItems()))).excludedItems(json(list(r.getExcludedItems()))).requiredInformation(json(list(r.getRequiredInformation()))).confirmationType(enumNameOrDefault(r.getConfirmationType(),"INSTANT")).voucherType(enumNameOrDefault(r.getVoucherType(),"QR_CODE")).validityDays(r.getValidityDays()).attributes(json(map(r.getAttributes()))).cancellationPolicy(json(map(r.getCancellationPolicy()))).status(enumNameOrDefault(r.getStatus(),MarketplaceAvailabilityStatus.ENABLED)).dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(n).updatedAt(n).build();try{repository.insertPackage(v);}catch(DataIntegrityViolationException ex){throw conflict("Package code already exists");}history.record(p.getOrganizationId(),"ACTIVITY_PACKAGE",v.getId(),"CREATED",v,List.of(),actor,"USER",null);return packageResponse(v);}
 @Transactional public ActivityPackageResponse partnerUpdatePackage(UUID actor,UUID id,UpsertActivityPackageRequest r){ActivityPackage v=pack(id);MarketplaceActivityProduct p=product(v.getActivityBookingId());authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"ACTIVITY_WRITE");validatePackage(r);long ver=requiredVersion(r.getExpectedVersion());v.setCode(code(r.getCode()));v.setName(r.getName().trim());v.setDescription(clean(r.getDescription()));v.setCurrency(r.getCurrency().toUpperCase());v.setBasePrice(r.getBasePrice());v.setOriginalPrice(r.getOriginalPrice());v.setPackageGroup(clean(r.getPackageGroup()));v.setGroupType(r.getGroupType()==null?null:r.getGroupType().name());v.setDepartureType(r.getDepartureType()==null?null:r.getDepartureType().name());v.setDetails(json(r.getDetails()==null?Map.of():r.getDetails()));v.setMinQuantity(r.getMinQuantity());v.setMaxQuantity(r.getMaxQuantity());v.setInventoryType(enumNameOrDefault(r.getInventoryType(),v.getInventoryType()));v.setUnits(json(list(r.getUnits())));v.setIncludedItems(json(list(r.getIncludedItems())));v.setExcludedItems(json(list(r.getExcludedItems())));v.setRequiredInformation(json(list(r.getRequiredInformation())));v.setConfirmationType(enumNameOrDefault(r.getConfirmationType(),v.getConfirmationType()));v.setVoucherType(enumNameOrDefault(r.getVoucherType(),v.getVoucherType()));v.setValidityDays(r.getValidityDays());v.setAttributes(json(map(r.getAttributes())));v.setCancellationPolicy(json(map(r.getCancellationPolicy())));v.setStatus(enumNameOrDefault(r.getStatus(),v.getStatus()));v.setDataVersion(ver);v.setUpdatedBy(actor);v.setUpdatedAt(LocalDateTime.now());try{optimistic(repository.updatePackage(v),"Package");}catch(DataIntegrityViolationException ex){throw conflict("Package update violates code or quantity constraints");}v.setDataVersion(ver+1);history.record(p.getOrganizationId(),"ACTIVITY_PACKAGE",id,"UPDATED",v,List.of("PACKAGE"),actor,"USER",null);return packageResponse(v);}
 public List<ActivitySlotResponse> partnerSlots(UUID actor,UUID id,LocalDateTime from){ActivityPackage a=pack(id);MarketplaceActivityProduct p=product(a.getActivityBookingId());authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"ACTIVITY_READ");return repository.findSlots(id,from==null?LocalDateTime.now().minusYears(1):from,true).stream().map(this::slotResponse).toList();}

 @Transactional public ActivitySlotResponse partnerCreateSlot(UUID actor,UUID id,UpsertActivitySlotRequest r){ActivityPackage a=pack(id);MarketplaceActivityProduct p=product(a.getActivityBookingId());authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"SLOT_WRITE");rejectExistingSlotStarts(id,List.of(r));return createSlot(actor,a,p,r);}
 @Transactional public List<ActivitySlotResponse> partnerCreateSlots(UUID actor,UUID id,BulkCreateActivitySlotsRequest request){ActivityPackage a=pack(id);MarketplaceActivityProduct p=product(a.getActivityBookingId());authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"SLOT_WRITE");List<UpsertActivitySlotRequest> requests=request.getSlots();rejectExistingSlotStarts(id,requests);return requests.stream().map(r->createSlot(actor,a,p,r)).toList();}
 @Transactional public ActivitySlotResponse partnerUpdateSlot(UUID actor,UUID id,UpsertActivitySlotRequest r){ActivitySlot s=slot(id);ActivityPackage a=pack(s.getPackageId());MarketplaceActivityProduct p=product(a.getActivityBookingId());authorization.requireResourcePermission(p.getOrganizationId(),actor,"ACTIVITY",p.getId(),"SLOT_WRITE");validateSlot(a,r);long v=requiredVersion(r.getExpectedVersion());s.setStartsAt(r.getStartsAt());s.setEndsAt(r.getEndsAt());s.setTimezone(r.getTimezone());s.setCapacity(r.getCapacity());s.setBlockedQuantity(r.getBlockedQuantity());s.setBookingCutoffMinutes(r.getBookingCutoffMinutes());s.setPriceOverride(r.getPriceOverride());s.setUnitPrices(json(map(r.getUnitPrices())));s.setAllDay(Boolean.TRUE.equals(r.getAllDay()));s.setMeetingPointOverride(clean(r.getMeetingPointOverride()));String nextSlotStatus=enumNameOrDefault(r.getStatus(),s.getStatus());
  if(!MarketplaceSlotStatus.ENABLED.name().equals(nextSlotStatus)&&MarketplaceSlotStatus.ENABLED.name().equals(s.getStatus())){
   long live=repository.countActiveOrdersForSlot(id);
   // Cancelling a slot under live orders would leave guests holding a ticket for a departure that is gone.
   if(live>0)throw conflict("This slot still has "+live+" active order(s). Cancel them (CANCELLED_BY_HOST) before closing the slot");
  }
  s.setStatus(nextSlotStatus);s.setDataVersion(v);s.setUpdatedBy(actor);s.setUpdatedAt(LocalDateTime.now());optimistic(repository.updateSlot(s),"Slot");s.setDataVersion(v+1);history.record(p.getOrganizationId(),"ACTIVITY_SLOT",id,"UPDATED",s,List.of("SCHEDULE","CAPACITY","PRICING"),actor,"USER",null);return slotResponse(repository.findSlot(id).orElse(s));}

 /**
  * A cart line is shown, not ordered, so an unbookable selection is an answer rather than an error.
  * Mirrors createOrder's checks, including the remaining-capacity test that reserveSlot performs in
  * SQL, so a line that says available is one createOrder would accept at this instant.
  */
 public MarketplaceQuoteResponse quoteActivity(UUID activityId,UUID packageId,UUID slotId,Map<String,Integer> unitQuantities,Integer quantity){
  try{
   MarketplaceActivityProduct p=publicProduct(activityId);ActivityPackage a=pack(packageId);ActivitySlot s=slot(slotId);
   if(!MarketplaceAvailabilityStatus.ENABLED.name().equals(a.getStatus())||!MarketplaceSlotStatus.ENABLED.name().equals(s.getStatus())
     ||!a.getActivityBookingId().equals(p.getId())||!s.getPackageId().equals(a.getId()))throw notFound("Bookable activity option not found");
   if(s.getStartsAt().minusMinutes(s.getBookingCutoffMinutes()).isBefore(LocalDateTime.now()))throw conflict("The booking cutoff for this activity slot has passed");
   CreateActivityOrderRequest probe=new CreateActivityOrderRequest();
   probe.setActivityId(activityId);probe.setPackageId(packageId);probe.setSlotId(slotId);
   probe.setUnitQuantities(unitQuantities);probe.setQuantity(quantity==null?1:quantity);
   OrderPricing pricing=orderPricing(a,s,probe);
   if(pricing.saleQuantity()<a.getMinQuantity()||a.getMaxQuantity()!=null&&pricing.saleQuantity()>a.getMaxQuantity())throw bad("Quantity is outside package limits");
   int taken=nullToZero(s.getReservedQuantity())+nullToZero(s.getSoldQuantity())+nullToZero(s.getBlockedQuantity());
   if(taken+pricing.capacityQuantity()>nullToZero(s.getCapacity()))throw conflict("Activity slot is no longer available");
   return MarketplaceQuoteResponse.builder().available(true).currency(a.getCurrency()).totalAmount(pricing.total()).build();
  }catch(BusinessException ex){
   return MarketplaceQuoteResponse.builder().available(false).unavailableReason(ex.getMessage()).build();
  }
 }
 private int nullToZero(Integer value){return value==null?0:value;}

 @Transactional public ActivityOrderResponse createOrder(UUID user,CreateActivityOrderRequest r){
  String idempotencyKey=clean(r.getIdempotencyKey());if(idempotencyKey!=null){ActivityOrder existing=repository.findOrderByUserAndIdempotencyKey(user,idempotencyKey).orElse(null);if(existing!=null)return orderResponse(existing);}
  MarketplaceActivityProduct p=publicProduct(r.getActivityId());ActivityPackage a=pack(r.getPackageId());ActivitySlot s=slot(r.getSlotId());
  if(!MarketplaceAvailabilityStatus.ENABLED.name().equals(a.getStatus())||!MarketplaceSlotStatus.ENABLED.name().equals(s.getStatus())||!a.getActivityBookingId().equals(p.getId())||!s.getPackageId().equals(a.getId()))throw notFound("Bookable activity option not found");
  if(s.getStartsAt().minusMinutes(s.getBookingCutoffMinutes()).isBefore(LocalDateTime.now()))throw conflict("The booking cutoff for this activity slot has passed");
  OrderPricing pricing=orderPricing(a,s,r);int q=pricing.capacityQuantity();BigDecimal total=pricing.total();
  if(pricing.saleQuantity()<a.getMinQuantity()||a.getMaxQuantity()!=null&&pricing.saleQuantity()>a.getMaxQuantity())throw bad("Quantity is outside package limits");
  LocalDateTime n=LocalDateTime.now();if(repository.reserveSlot(s.getId(),q,user,n)!=1)throw conflict("Activity slot is no longer available");
  boolean manualConfirmation=MarketplaceConfirmationType.MANUAL.name().equals(a.getConfirmationType());
  if(!manualConfirmation&&repository.confirmSlot(s.getId(),q,user,n)!=1)throw conflict("Reserved slot inventory is inconsistent");
  Map<String,Object> snap=new LinkedHashMap<>();snap.put("activity",p);snap.put("package",a);snap.put("packageVersion",a.getDataVersion());snap.put("cancellationPolicy",readMap(a.getCancellationPolicy()));snap.put("confirmationType",a.getConfirmationType());snap.put("slot",s);snap.put("unitSelections",pricing.selections());
  ActivityOrder o=ActivityOrder.builder().id(UUID.randomUUID()).orderCode(orderCode()).userId(user).organizationId(p.getOrganizationId()).activityBookingId(p.getId()).slotId(s.getId()).participantInfo(json(list(r.getParticipants()))).contactInfo(json(map(r.getContactInfo()))).specialRequests(clean(r.getSpecialRequests())).redemptionStatus(com.ds.goroute.type.MarketplaceRedemptionStatus.NOT_REDEEMED.name()).idempotencyKey(idempotencyKey).holdExpiresAt(manualConfirmation?partnerConfirmationDeadline(n):null).currency(a.getCurrency()).subtotalAmount(total).taxAmount(BigDecimal.ZERO).feeAmount(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO).totalAmount(total).orderStatus(manualConfirmation?MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name():MarketplaceBookingStatus.CONFIRMED.name()).voucherCode(manualConfirmation?null:voucherCode()).paymentStatus(MarketplacePaymentStatus.NOT_COLLECTED.name()).snapshot(json(snap)).dataVersion(1L).createdBy(user).updatedBy(user).createdAt(n).updatedAt(n).build();
  BigDecimal averageUnitPrice=total.divide(BigDecimal.valueOf(q),2,java.math.RoundingMode.HALF_UP);
  ActivityOrderItem i=ActivityOrderItem.builder().id(UUID.randomUUID()).orderId(o.getId()).packageId(a.getId()).quantity(q).unitPrice(averageUnitPrice).totalPrice(total).unitSelections(json(pricing.selections())).snapshot(json(snap)).createdAt(n).build();
  repository.insertOrder(o);repository.insertOrderItem(i);commissionService.stampCommission("ACTIVITY",o.getId());history.record(p.getOrganizationId(),"ACTIVITY_ORDER",o.getId(),manualConfirmation?"REQUESTED":"CONFIRMED",o,List.of(),user,"USER",null);if(manualConfirmation)notifyPartnerRequest(p.getOrganizationId(),p.getId(),o.getId(),o.getOrderCode(),o.getHoldExpiresAt(),user);else {notifyPartnerConfirmed(p.getOrganizationId(),p.getId(),o.getId(),o.getOrderCode(),user);notifyGuestStatus(o,MarketplaceBookingStatus.CONFIRMED,null,user);}return orderResponse(repository.findOrder(o.getId()).orElse(o));
 }
 public List<ActivityOrderResponse> listMyOrders(UUID u,int p,int s){Page r=page(p,s);return repository.findOrdersByUser(u,r.limit,r.offset).stream().map(this::orderResponse).toList();}
 @Override public List<UUID> findExpiredPendingOrderIds(LocalDateTime now,int limit){return repository.findExpiredPendingOrders(now,limit).stream().map(ActivityOrder::getId).toList();}
 @Override @Transactional public boolean expirePendingOrder(UUID orderId,LocalDateTime now){
  ActivityOrder order=repository.findOrder(orderId).orElse(null);
  if(order==null||!MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name().equals(order.getOrderStatus()))return false;
  if(repository.expireOrderHold(order.getId(),order.getDataVersion(),null,now)!=1)return false;
  ActivityOrderItem item=repository.findOrderItem(order.getId()).orElseThrow(()->notFound("Order item not found"));
  if(repository.releaseSlot(order.getSlotId(),item.getQuantity(),true,null,now)!=1)throw conflict("Expired activity order inventory is inconsistent");
  history.record(order.getOrganizationId(),"ACTIVITY_ORDER",order.getId(),"REQUEST_EXPIRED",order,List.of("orderStatus"),null,"SYSTEM","Partner confirmation deadline expired");
  notifyGuestLifecycle(order,MarketplaceBookingStatus.EXPIRED,null,null);
  return true;
 }
 public ActivityOrderResponse getMyOrder(UUID u,UUID id){ActivityOrder o=order(id);if(!u.equals(o.getUserId()))throw forbidden();return orderResponse(o);}
 public CancellationPreviewResponse previewMyCancellation(UUID u,UUID id){ActivityOrder o=order(id);if(!u.equals(o.getUserId()))throw forbidden();return cancellationPreview(o);}
 @Transactional public ActivityOrderResponse cancelMyOrder(UUID u,UUID id,String reason,Long v){
  ActivityOrder o=order(id);if(!u.equals(o.getUserId()))throw forbidden();
  CancellationPreviewResponse preview=cancellationPreview(o);
  if(!preview.isCancellable())throw bad(preview.getBlockedReason());
  String outcome=preview.isFree()?"free cancellation":"penalty "+preview.getPenaltyAmount()+" "+preview.getCurrency()+" ("+preview.getPenaltyRule()+")";
  String recorded=(clean(reason)==null?"Cancelled by guest":reason.trim())+" — "+outcome;
  return transition(o,MarketplaceBookingStatus.CANCELLED_BY_GUEST,recorded,v==null?o.getDataVersion():v,null,u,"USER");
 }
 private CancellationPreviewResponse cancellationPreview(ActivityOrder o){
  MarketplaceBookingStatus current;try{current=MarketplaceBookingStatus.valueOf(o.getOrderStatus());}catch(IllegalArgumentException ex){throw bad("Invalid stored order status: "+o.getOrderStatus());}
  ActivitySlot s=slot(o.getSlotId());
  LocalDateTime now=slotNow(s);LocalDateTime start=s.getStartsAt();
  CancellationPreviewResponse.CancellationPreviewResponseBuilder out=CancellationPreviewResponse.builder().currency(o.getCurrency()).serviceStartsAt(start);
  if(!current.isGuestCancellable())return out.cancellable(false).blockedReason("This order is "+current.name().toLowerCase(Locale.ROOT).replace('_',' ')+" and can no longer be cancelled").build();
  if(current==MarketplaceBookingStatus.CONFIRMED&&start!=null&&!now.isBefore(start))return out.cancellable(false).blockedReason("The activity has already started; please contact the operator").build();
  Map<String,Object> snapshot=readMap(o.getSnapshot());Object policy=snapshot.get("cancellationPolicy");
  @SuppressWarnings("unchecked") Map<String,Object> policyMap=policy instanceof Map<?,?> m?(Map<String,Object>)m:Map.of();
  CancellationPolicyEvaluator.Outcome outcome=current==MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION
   ?new CancellationPolicyEvaluator.Outcome(String.valueOf(policyMap.getOrDefault("type","FLEXIBLE")),true,start,BigDecimal.ZERO,"PENDING_REQUEST")
   :CancellationPolicyEvaluator.evaluate(policyMap,start,now,o.getTotalAmount(),null);
  return out.cancellable(true).policyType(outcome.policyType()).free(outcome.free()).freeUntil(outcome.freeUntil()).penaltyAmount(outcome.penaltyAmount()).penaltyRule(outcome.penaltyRule()).build();
 }
 /** "Now" expressed in the slot's own timezone, so a Tokyo slot is compared with Tokyo wall-clock time, not the server's. */
 private LocalDateTime slotNow(ActivitySlot s){try{return s.getTimezone()==null?LocalDateTime.now():LocalDateTime.now(ZoneId.of(s.getTimezone()));}catch(Exception ex){return LocalDateTime.now();}}
 public PageResponse<ActivityOrderResponse> partnerOrders(UUID a,UUID org,String status,UUID activityId,int p,int s){
  authorization.requireOrganization(org,a);
  // One product at a time is its own permission check, so a member scoped out of it gets 403 instead of an empty page.
  List<UUID> productIds;
  if(activityId!=null){authorization.requireResourcePermission(org,a,"ACTIVITY",activityId,"ORDER_READ");productIds=List.of(activityId);}
  else productIds=authorization.accessibleResourceIds(org,a,"ACTIVITY",repository.findProductsByOrganization(org).stream().map(MarketplaceActivityProduct::getId).toList(),"ORDER_READ");
  Page r=page(p,s);
  if(productIds!=null&&productIds.isEmpty())return PageResponse.of(List.of(),0,Math.max(p,0),r.limit);
  long total=repository.countOrdersByOrganizationFiltered(org,clean(status),productIds);
  List<ActivityOrderResponse> items=repository.findOrdersByOrganization(org,clean(status),productIds,r.limit,r.offset).stream().map(this::orderResponse).toList();
  return PageResponse.of(items,total,Math.max(p,0),r.limit);
 }
 public ListingReadinessResponse partnerProductReadiness(UUID a,UUID id){MarketplaceActivityProduct p=product(id);authorization.requireResourcePermission(p.getOrganizationId(),a,"ACTIVITY",p.getId(),"ACTIVITY_READ");return ListingReadinessResponse.from(productReadiness(p));}
 private ListingReadiness.Result productReadiness(MarketplaceActivityProduct p){
  List<ActivityPackage> packages=repository.findPackages(p.getId(),true).stream().filter(x->MarketplaceAvailabilityStatus.ENABLED.name().equals(x.getStatus())).toList();
  LocalDateTime now=LocalDateTime.now();
  boolean upcoming=packages.stream().anyMatch(x->repository.findSlots(x.getId(),now,false).stream().anyMatch(sl->MarketplaceSlotStatus.ENABLED.name().equals(sl.getStatus())&&!sl.getStartsAt().isAfter(now.plusDays(30))&&sl.getAvailableQuantity()!=null&&sl.getAvailableQuantity()>0));
  boolean policy=packages.stream().anyMatch(x->readMap(x.getCancellationPolicy()).get("type")!=null);
  int photos=(p.getThumbnail()==null?0:1)+readList(p.getImages(),String.class).size();
  String description=p.getDescription()==null?"":p.getDescription().trim();
  List<ListingReadiness.Check> checks=List.of(
   ListingReadiness.of("PHOTOS_MIN_3","At least 3 photos",photos>=3,false,10,photos+" photo(s)"),
   ListingReadiness.of("DESCRIPTION","Description of at least 80 characters",description.length()>=80,false,10,description.length()+" characters"),
   ListingReadiness.of("MEETING_POINT","Meeting point or pickup",p.getMeetingPoint()!=null&&!p.getMeetingPoint().isBlank(),true,10,null),
   ListingReadiness.of("PACKAGE_ENABLED","At least one enabled package",!packages.isEmpty(),true,20,packages.size()+" enabled"),
   ListingReadiness.of("SLOT_UPCOMING_30D","An open slot in the next 30 days",upcoming,true,20,null),
   ListingReadiness.of("CANCELLATION_POLICY","Cancellation policy on an enabled package",policy,true,10,null),
   ListingReadiness.of("INCLUDED_ITEMS","What is included",!readList(p.getIncludedItems(),String.class).isEmpty(),false,10,null),
   ListingReadiness.of("DURATION","Duration",p.getDurationHours()!=null&&p.getDurationHours().signum()>0,false,10,null));
  return ListingReadiness.score(checks);
 }
 public SlotQuote quoteSlotChange(UUID orderId,UUID slotId){
  ActivityOrder o=order(orderId);ActivityOrderItem item=repository.findOrderItem(o.getId()).orElseThrow(()->notFound("Order item not found"));
  try{ActivitySlot target=slot(slotId);ActivityPackage pkg=pack(item.getPackageId());validateSlotChange(o,item,pkg,target);OrderPricing pricing=orderPricing(pkg,target,requestFromItem(item));return new SlotQuote(true,null,pricing.total(),o.getCurrency());}
  catch(BusinessException ex){return new SlotQuote(false,ex.getMessage(),null,o.getCurrency());}
 }
 @Transactional public ActivityOrderResponse partnerApplySlotChange(UUID a,UUID orderId,UUID slotId,Long expectedVersion){
  ActivityOrder o=order(orderId);authorization.requireResourcePermission(o.getOrganizationId(),a,"ACTIVITY",o.getActivityBookingId(),"ORDER_WRITE");
  MarketplaceBookingStatus status;try{status=MarketplaceBookingStatus.valueOf(o.getOrderStatus());}catch(IllegalArgumentException ex){throw bad("Invalid stored order status");}
  if(status!=MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION&&status!=MarketplaceBookingStatus.CONFIRMED)throw bad("Only pending or confirmed orders can be changed");
  ActivityOrderItem item=repository.findOrderItem(o.getId()).orElseThrow(()->notFound("Order item not found"));ActivitySlot target=slot(slotId);ActivityPackage pkg=pack(item.getPackageId());
  validateSlotChange(o,item,pkg,target);
  LocalDateTime now=LocalDateTime.now();boolean reserved=status==MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION;
  if(repository.releaseSlot(o.getSlotId(),item.getQuantity(),reserved,a,now)!=1)throw conflict("Allocated slot inventory is inconsistent");
  if(repository.reserveSlot(target.getId(),item.getQuantity(),a,now)!=1)throw conflict("The requested slot is no longer available");
  if(!reserved&&repository.confirmSlot(target.getId(),item.getQuantity(),a,now)!=1)throw conflict("Reserved slot inventory is inconsistent");
  OrderPricing pricing=orderPricing(pkg,target,requestFromItem(item));BigDecimal total=pricing.total();
  Map<String,Object> snapshot=new LinkedHashMap<>(readMap(o.getSnapshot()));
  @SuppressWarnings("unchecked") List<Object> changes=snapshot.get("changeHistory") instanceof List<?> l?new ArrayList<>((List<Object>)l):new ArrayList<>();
  changes.add(Map.of("at",now.toString(),"fromSlotId",String.valueOf(o.getSlotId()),"toSlotId",target.getId().toString(),"priceBefore",o.getTotalAmount(),"priceAfter",total,"by",a.toString()));
  snapshot.put("changeHistory",changes);snapshot.put("slot",target);
  optimistic(repository.updateOrderSlot(o.getId(),requiredVersion(expectedVersion),target.getId(),total,total,json(snapshot),a,now),"Activity order");
  repository.updateOrderItemPrice(item.getId(),total.divide(BigDecimal.valueOf(Math.max(1,item.getQuantity())),2,java.math.RoundingMode.HALF_UP),total);
  ActivityOrder saved=order(o.getId());history.record(saved.getOrganizationId(),"ACTIVITY_ORDER",saved.getId(),"SLOT_CHANGED",saved,List.of("slotId","totalAmount"),a,"USER",null);
  return orderResponse(saved);
 }
 private void validateSlotChange(ActivityOrder o,ActivityOrderItem item,ActivityPackage pkg,ActivitySlot target){
  if(!target.getPackageId().equals(item.getPackageId()))throw bad("The new slot must belong to the same package");
  if(!MarketplaceSlotStatus.ENABLED.name().equals(target.getStatus()))throw conflict("The requested slot is not open");
  if(target.getStartsAt().minusMinutes(target.getBookingCutoffMinutes()==null?0:target.getBookingCutoffMinutes()).isBefore(slotNow(target)))throw conflict("The booking cutoff for the requested slot has passed");
  int available=target.getAvailableQuantity()==null?0:target.getAvailableQuantity();if(available<item.getQuantity()&&!target.getId().equals(o.getSlotId()))throw conflict("The requested slot does not have enough capacity");
 }
 /** Rebuilds the pricing request from what the guest originally selected, so a slot change is priced with the same units. */
 private CreateActivityOrderRequest requestFromItem(ActivityOrderItem item){
  CreateActivityOrderRequest r=new CreateActivityOrderRequest();Map<String,Object> selections=readMap(item.getUnitSelections());
  if(selections.isEmpty()){r.setQuantity(item.getQuantity());return r;}
  Map<String,Integer> units=new LinkedHashMap<>();
  selections.forEach((code,v)->{Integer q=null;if(v instanceof Number n)q=n.intValue();else if(v instanceof Map<?,?> m&&m.get("quantity") instanceof Number n)q=n.intValue();if(q!=null&&q>0)units.put(code,q);});
  if(units.isEmpty())r.setQuantity(item.getQuantity());else r.setUnitQuantities(units);return r;
 }
 @Transactional public ActivityOrderResponse partnerRedeem(UUID a,UUID id){return redeem(a,order(id));}
 @Transactional public ActivityOrderResponse partnerRedeemByVoucher(UUID a,UUID org,String code){authorization.requireOrganization(org,a);String normalized=code==null?"":code.trim().toUpperCase(Locale.ROOT);ActivityOrder o=repository.findOrderByVoucherCode(org,normalized).orElseThrow(()->notFound("No order matches this voucher code"));return redeem(a,o);}
 /** Scanning a voucher is the activity equivalent of a hotel check-in: CONFIRMED -> CHECKED_IN plus the redemption stamp. */
 private ActivityOrderResponse redeem(UUID a,ActivityOrder o){
  authorization.requireResourcePermission(o.getOrganizationId(),a,"ACTIVITY",o.getActivityBookingId(),"ORDER_WRITE");
  if(MarketplaceBookingStatus.CHECKED_IN.name().equals(o.getOrderStatus())||"REDEEMED".equals(o.getRedemptionStatus()))throw conflict("This voucher was already redeemed");
  if(!MarketplaceBookingStatus.CONFIRMED.name().equals(o.getOrderStatus()))throw bad("Only a confirmed order can be redeemed (current: "+o.getOrderStatus()+")");
  if(o.getVoucherCode()==null)throw bad("This order has no voucher yet");
  ActivityOrderResponse r=transition(o,MarketplaceBookingStatus.CHECKED_IN,"Voucher redeemed",o.getDataVersion(),null,a,"USER");
  repository.markRedeemed(o.getId(),a,LocalDateTime.now());
  return orderResponse(order(o.getId()));
 }
 @SuppressWarnings("unchecked") private String voucherTypeOf(ActivityOrder o){Map<String,Object> snap=readMap(o.getSnapshot());Object pkg=snap.get("package");if(pkg instanceof Map<?,?> m&&m.get("voucherType")!=null)return String.valueOf(m.get("voucherType"));return "QR_CODE";}
 private String voucherCode(){String alphabet="ABCDEFGHJKLMNPQRSTUVWXYZ23456789";StringBuilder b=new StringBuilder("VCH-");java.security.SecureRandom rnd=new java.security.SecureRandom();for(int i=0;i<8;i++)b.append(alphabet.charAt(rnd.nextInt(alphabet.length())));return b.toString();}
 public ActivityOrderResponse partnerOrder(UUID a,UUID id){ActivityOrder o=order(id);authorization.requireResourcePermission(o.getOrganizationId(),a,"ACTIVITY",o.getActivityBookingId(),"ORDER_READ");return orderResponse(o);}
 @Transactional public ActivityOrderResponse partnerOrderStatus(UUID a,UUID id,UpdateActivityOrderStatusRequest r){ActivityOrder o=order(id);authorization.requireResourcePermission(o.getOrganizationId(),a,"ACTIVITY",o.getActivityBookingId(),"ORDER_WRITE");requireOperatorTarget(r.getOrderStatus(),false);return transition(o,r.getOrderStatus(),r.getReason(),r.getExpectedVersion(),r.getGuestCharged(),a,"USER");}
 public List<MarketplaceActivityResponse> adminProducts(String q,String s,int p,int z){Page r=page(p,z);return repository.findProductsAdmin(clean(q),clean(s),r.limit,r.offset).stream().map(this::productResponse).toList();}
 public MarketplaceActivityResponse adminProduct(UUID id){return productResponse(product(id));}
 public List<ActivityPackageResponse> adminPackages(UUID id){product(id);return repository.findPackages(id,true).stream().map(this::packageResponse).toList();}
 public List<ActivitySlotResponse> adminSlots(UUID id,LocalDateTime from){pack(id);return repository.findSlots(id,from==null?LocalDateTime.now().minusYears(1):from,true).stream().map(this::slotResponse).toList();}
 public ActivityOrderResponse adminOrder(UUID id){return orderResponse(order(id));}
 @Transactional public MarketplaceActivityResponse adminProductStatus(UUID actor,UUID id,MarketplacePublicationStatus status,String reason,Long expectedVersion){MarketplaceActivityProduct p=product(id);p.setProductStatus(status.name());p.setDataVersion(requiredVersion(expectedVersion));p.setUpdatedBy(actor);p.setUpdatedAt(LocalDateTime.now());optimistic(repository.updateProduct(p),"Activity");p.setDataVersion(p.getDataVersion()+1);history.record(p.getOrganizationId(),"ACTIVITY_PRODUCT",id,"ADMIN_STATUS_CHANGED",p,List.of("productStatus"),actor,"ADMIN",reason);return productResponse(repository.findProduct(id).orElse(p));}
 public List<ActivityOrderResponse> adminOrders(String q,String s,int p,int z){Page r=page(p,z);return repository.findOrdersAdmin(clean(q),clean(s),r.limit,r.offset).stream().map(this::orderResponse).toList();}
 @Transactional public ActivityOrderResponse adminOrderStatus(UUID actor,UUID id,UpdateActivityOrderStatusRequest r){requireOperatorTarget(r.getOrderStatus(),true);return transition(order(id),r.getOrderStatus(),r.getReason(),r.getExpectedVersion(),r.getGuestCharged(),actor,"ADMIN");}
 @Transactional public MarketplaceActivityResponse adminCreateProduct(UUID actor,UpsertMarketplaceActivityRequest r){return partnerCreate(actor,r);}@Transactional public MarketplaceActivityResponse adminUpdateProduct(UUID actor,UUID id,UpsertMarketplaceActivityRequest r){long expected=requiredVersion(r.getExpectedVersion());MarketplaceActivityProduct current=product(id);if(MarketplacePublicationStatus.SUSPENDED.name().equals(current.getProductStatus())){adminProductStatus(actor,id,MarketplacePublicationStatus.DISABLED,"Admin editing suspended activity",expected);r.setExpectedVersion(expected+1);}return partnerUpdate(actor,id,r);}
 @Transactional public ActivityPackageResponse adminCreatePackage(UUID actor,UUID activityId,UpsertActivityPackageRequest r){return partnerCreatePackage(actor,activityId,r);}@Transactional public ActivityPackageResponse adminUpdatePackage(UUID actor,UUID id,UpsertActivityPackageRequest r){return partnerUpdatePackage(actor,id,r);}
 @Transactional public ActivitySlotResponse adminCreateSlot(UUID actor,UUID packageId,UpsertActivitySlotRequest r){return partnerCreateSlot(actor,packageId,r);}@Transactional public ActivitySlotResponse adminUpdateSlot(UUID actor,UUID id,UpsertActivitySlotRequest r){return partnerUpdateSlot(actor,id,r);}

 private ActivityOrderResponse transition(ActivityOrder o,MarketplaceBookingStatus target,String reason,Long requested,Boolean guestCharged,UUID actor,String type){try{if(!MarketplaceBookingStatus.valueOf(o.getOrderStatus()).canTransitionTo(target))throw bad("Invalid order transition: "+o.getOrderStatus()+" -> "+target);MarketplacePaymentStatus.requireCompatible(target,o.getPaymentStatus());}catch(IllegalArgumentException ex){throw bad(ex.getMessage());}ActivityOrderItem i=repository.findOrderItem(o.getId()).orElseThrow(()->notFound("Order item not found"));if(target==MarketplaceBookingStatus.NO_SHOW){ActivitySlot noShowSlot=slot(o.getSlotId());LocalDateTime nowInSlot=slotNow(noShowSlot);if(nowInSlot.isBefore(noShowSlot.getStartsAt()))throw bad("An activity order can be marked no-show only after its slot starts");LocalDateTime windowEnd=(noShowSlot.getEndsAt()==null?noShowSlot.getStartsAt():noShowSlot.getEndsAt()).plusHours(48);if(nowInSlot.isAfter(windowEnd))throw bad("The no-show window closed 48 hours after the activity; contact support");}if(target.confirmsReservedInventory()){if(repository.confirmSlot(o.getSlotId(),i.getQuantity(),actor,LocalDateTime.now())!=1)throw conflict("Reserved slot inventory is inconsistent");}else if(target.releasesInventory()){boolean reserved=MarketplaceBookingStatus.PENDING_PARTNER_CONFIRMATION.name().equals(o.getOrderStatus());if(repository.releaseSlot(o.getSlotId(),i.getQuantity(),reserved,actor,LocalDateTime.now())!=1)throw conflict("Allocated slot inventory is inconsistent");}optimistic(repository.updateOrderStatus(o.getId(),requiredVersion(requested),target.name(),target==MarketplaceBookingStatus.NO_SHOW?guestCharged:null,actor,LocalDateTime.now()),"Activity order");if(target==MarketplaceBookingStatus.CONFIRMED&&o.getVoucherCode()==null)repository.assignVoucher(o.getId(),voucherCode(),LocalDateTime.now());if(target.releasesInventory()&&o.getVoucherCode()!=null)repository.voidVoucher(o.getId(),LocalDateTime.now());ActivityOrder saved=order(o.getId());history.record(saved.getOrganizationId(),"ACTIVITY_ORDER",saved.getId(),"STATUS_CHANGED",saved,List.of("orderStatus"),actor,type,reason);if(target==MarketplaceBookingStatus.CONFIRMED||target==MarketplaceBookingStatus.CANCELLED_BY_HOST)notifyGuestStatus(saved,target,reason,actor);else if(target==MarketplaceBookingStatus.CANCELLED_BY_GUEST)notifyPartnerGuestCancelled(saved,actor);else notifyGuestLifecycle(saved,target,reason,actor);if(target==MarketplaceBookingStatus.COMPLETED)notifyReviewInvite(saved,actor);return orderResponse(saved);}
 private void requireOperatorTarget(MarketplaceBookingStatus target,boolean admin){
  if(target==null)throw bad("orderStatus is required");
  if(admin?target.canBeSetByAdmin():target.canBeSetByPartner())return;
  if(target==MarketplaceBookingStatus.CANCELLED_BY_GUEST)throw bad("Only the guest can set CANCELLED_BY_GUEST");
  if(target.isSystemOnly())throw bad("Status "+target+" is set by the system only");
  throw bad("Status "+target+" can only be set by the platform");
 }
 private void applyProductDetails(MarketplaceActivityProduct p,UpsertMarketplaceActivityRequest r){List<String> images=list(r.getImages());List<String> destinations=list(r.getDestinations());List<GeoCoordinateDto> coordinates=list(r.getDestinationCoordinates());validateCoordinates(coordinates);p.setActivityType(enumNameOrDefault(r.getActivityType(),"TOUR"));p.setUrl(clean(r.getUrl()));p.setRedirectUrl(clean(r.getRedirectUrl()));p.setTitle(r.getTitle().trim());p.setDescription(clean(r.getDescription()));p.setActivityAddress(clean(r.getActivityAddress()));p.setDepartingFrom(clean(r.getDepartingFrom()));p.setDestinations(json(destinations));p.setDestinationCoordinates(json(coordinates));p.setNavigationList(json(list(r.getNavigationList())));p.setItineraryStops(json(list(r.getItineraryStops())));p.setPickupAddresses(json(list(r.getPickupAddresses())));p.setLanguages(json(list(r.getLanguages())));p.setMeetingPoint(clean(r.getMeetingPoint()));p.setIncludedItems(json(list(r.getIncludedItems())));p.setExcludedItems(json(list(r.getExcludedItems())));p.setEligibility(json(map(r.getEligibility())));p.setAccessibilityFeatures(json(list(r.getAccessibilityFeatures())));p.setConfirmationType(enumNameOrDefault(r.getConfirmationType(),"INSTANT"));p.setVoucherType(enumNameOrDefault(r.getVoucherType(),"QR_CODE"));p.setRedemptionInstructions(clean(r.getRedemptionInstructions()));p.setCancellationPolicy(json(map(r.getCancellationPolicy())));p.setRequiredInformation(json(list(r.getRequiredInformation())));p.setSearchLat(coordinates.isEmpty()?null:coordinates.get(0).getLat().doubleValue());p.setSearchLng(coordinates.isEmpty()?null:coordinates.get(0).getLng().doubleValue());p.setDestinationsNorm(destinations.stream().filter(Objects::nonNull).map(value->value.trim().toLowerCase(Locale.ROOT)).filter(value->!value.isBlank()).collect(java.util.stream.Collectors.joining("|")));p.setPriceAmount(r.getPriceAmount());p.setPriceCurrency(r.getPriceCurrency().toUpperCase(Locale.ROOT));p.setDurationRaw(clean(r.getDurationRaw()));p.setDurationHours(r.getDurationHours());p.setVisitDurationMinutes(r.getVisitDurationMinutes());p.setThumbnail(clean(r.getThumbnail())==null?(images.isEmpty()?null:images.get(0)):clean(r.getThumbnail()));p.setImages(json(images));p.setHighlights(json(list(r.getHighlights())));p.setWhatToExpect(json(list(r.getWhatToExpect())));p.setItinerary(json(list(r.getItinerary())));p.setGoodToKnow(json(list(r.getGoodToKnow())));p.setFaqs(json(list(r.getFaqs())));p.setVideoUrl(clean(r.getVideoUrl()));}
 private void validateCoordinates(List<GeoCoordinateDto> values){for(GeoCoordinateDto value:values){if(value==null||value.getLat()==null||value.getLng()==null||value.getLat().compareTo(BigDecimal.valueOf(-90))<0||value.getLat().compareTo(BigDecimal.valueOf(90))>0||value.getLng().compareTo(BigDecimal.valueOf(-180))<0||value.getLng().compareTo(BigDecimal.valueOf(180))>0)throw bad("destinationCoordinates must contain valid latitude and longitude values");}}
 OrderPricing orderPricing(ActivityPackage pack,ActivitySlot slot,CreateActivityOrderRequest request){
  Map<String,Integer> rawRequested=map(request.getUnitQuantities());
  if(rawRequested.isEmpty()){
   int quantity=request.getQuantity()==null?1:request.getQuantity();BigDecimal price=slot.getPriceOverride()==null?pack.getBasePrice():slot.getPriceOverride();
   return new OrderPricing(quantity,quantity,price.multiply(BigDecimal.valueOf(quantity)),Map.of());
  }
  Map<String,Integer> requested=new LinkedHashMap<>();rawRequested.forEach((code,quantity)->requested.merge(code.trim().toUpperCase(Locale.ROOT),quantity,Integer::sum));
  List<ActivityPackageUnit> units=readList(pack.getUnits(),ActivityPackageUnit.class);if(units.isEmpty())throw bad("This package does not define bookable units");
  Map<String,ActivityPackageUnit> byCode=new LinkedHashMap<>();for(ActivityPackageUnit unit:units)byCode.put(unit.getCode().trim().toUpperCase(Locale.ROOT),unit);
  for(String code:requested.keySet())if(!byCode.containsKey(code))throw bad("Unknown package unit: "+code);
  Map<String,BigDecimal> rawSlotPrices=readValue(slot.getUnitPrices(),new TypeReference<Map<String,BigDecimal>>(){},Map.of());Map<String,BigDecimal> slotPrices=new HashMap<>();rawSlotPrices.forEach((code,price)->slotPrices.put(code.trim().toUpperCase(Locale.ROOT),price));
  Map<String,Object> selections=new LinkedHashMap<>();int capacity=0;int saleQuantity=0;BigDecimal total=BigDecimal.ZERO;
  for(ActivityPackageUnit unit:units){
   String code=unit.getCode().trim().toUpperCase(Locale.ROOT);int quantity=requested.getOrDefault(code,0);
   int minimum=unit.getMinQuantity()==null?0:unit.getMinQuantity();if(quantity<minimum)throw bad(code+" requires at least "+minimum);if(unit.getMaxQuantity()!=null&&quantity>unit.getMaxQuantity())throw bad(code+" exceeds maximum quantity");if(quantity==0)continue;
   int pax=unit.getPaxCount()==null?1:unit.getPaxCount();BigDecimal price=slotPrices.get(code);if(price==null)price=slot.getPriceOverride();if(price==null)price=unit.getPrice();if(price==null)price=pack.getBasePrice();
   BigDecimal subtotal=price.multiply(BigDecimal.valueOf(quantity));Map<String,Object> detail=new LinkedHashMap<>();detail.put("quantity",quantity);detail.put("paxCount",pax);detail.put("unitPrice",price);detail.put("subtotal",subtotal);selections.put(code,detail);saleQuantity+=quantity;capacity+=quantity*pax;total=total.add(subtotal);
  }
  if(capacity<1)throw bad("At least one package unit is required");return new OrderPricing(capacity,saleQuantity,total,selections);
 }
 private MarketplaceActivityResponse productResponse(MarketplaceActivityProduct p){return MarketplaceActivityResponse.builder().id(p.getId()).organizationId(p.getOrganizationId()).placeId(p.getPlaceId()).placeTitle(p.getPlaceTitle()).locationImageId(p.getLocationImageId()).externalId(p.getExternalId()).source(p.getSource()).activityType(p.getActivityType()).url(p.getUrl()).redirectUrl(p.getRedirectUrl()).title(p.getTitle()).description(p.getDescription()).activityAddress(p.getActivityAddress()).departingFrom(p.getDepartingFrom()).destinations(readList(p.getDestinations(),String.class)).destinationCoordinates(readList(p.getDestinationCoordinates(),GeoCoordinateDto.class)).navigationList(readList(p.getNavigationList(),String.class)).itineraryStops(readList(p.getItineraryStops(),String.class)).pickupAddresses(readList(p.getPickupAddresses(),String.class)).languages(readList(p.getLanguages(),String.class)).meetingPoint(p.getMeetingPoint()).includedItems(readList(p.getIncludedItems(),String.class)).excludedItems(readList(p.getExcludedItems(),String.class)).eligibility(readMap(p.getEligibility())).accessibilityFeatures(readList(p.getAccessibilityFeatures(),String.class)).confirmationType(p.getConfirmationType()).voucherType(p.getVoucherType()).redemptionInstructions(p.getRedemptionInstructions()).cancellationPolicy(readMap(p.getCancellationPolicy())).requiredInformation(readList(p.getRequiredInformation(),String.class)).priceAmount(p.getPriceAmount()).priceCurrency(p.getPriceCurrency()).durationRaw(p.getDurationRaw()).durationHours(p.getDurationHours()).visitDurationMinutes(p.getVisitDurationMinutes()).rating(p.getRating()).reviewCount(p.getReviewCount()).bookedCount(p.getBookedCount()).thumbnail(p.getThumbnail()).images(readList(p.getImages(),String.class)).highlights(readList(p.getHighlights(),String.class)).whatToExpect(readList(p.getWhatToExpect(),com.ds.goroute.dto.ActivityWhatToExpectItem.class)).itinerary(readList(p.getItinerary(),com.ds.goroute.dto.ActivityItineraryItem.class)).goodToKnow(readList(p.getGoodToKnow(),String.class)).faqs(readList(p.getFaqs(),com.ds.goroute.dto.ActivityFaqItem.class)).videoUrl(p.getVideoUrl()).productKind(com.ds.goroute.type.ActivityProductType.kindOf(p.getActivityType())).operatorName(p.getOperatorName()).productStatus(p.getProductStatus()).inventoryMode(p.getInventoryMode()).dataVersion(p.getDataVersion()).createdAt(p.getCreatedAt()).updatedAt(p.getUpdatedAt()).build();}
 /**
  * Public catalogue prices are shown in the guest's currency; the order that
  * follows still settles in the package currency. Partner and admin endpoints
  * share the response mappers above, so they must stay unconverted.
  */
 private MarketplaceActivityResponse displayed(MarketplaceActivityResponse r){if(r.getPriceAmount()==null)return r;r.setPriceAmount(displayPrice.convert(r.getPriceAmount(),r.getPriceCurrency()));r.setPriceCurrency(displayPrice.currencyOf(r.getPriceCurrency()));return r;}
 private ActivityPackageResponse displayed(ActivityPackageResponse r){String stored=r.getCurrency();r.setBasePrice(displayPrice.convert(r.getBasePrice(),stored));r.setOriginalPrice(displayPrice.convert(r.getOriginalPrice(),stored));if(r.getUnits()!=null)r.setUnits(r.getUnits().stream().map(u->ActivityPackageUnit.builder().code(u.getCode()).name(u.getName()).unitType(u.getUnitType()).price(displayPrice.convert(u.getPrice(),stored)).minAge(u.getMinAge()).maxAge(u.getMaxAge()).minQuantity(u.getMinQuantity()).maxQuantity(u.getMaxQuantity()).paxCount(u.getPaxCount()).idRequired(u.getIdRequired()).build()).toList());r.setCurrency(displayPrice.currencyOf(stored));return r;}
 /** A slot has no currency of its own: it prices the package it belongs to. */
 private ActivitySlotResponse displayed(ActivitySlotResponse r,String packageCurrency){r.setPriceOverride(displayPrice.convert(r.getPriceOverride(),packageCurrency));r.setUnitPrices(displayPrice.convert(r.getUnitPrices(),packageCurrency));return r;}

 private ActivityPackageResponse packageResponse(ActivityPackage a){return ActivityPackageResponse.builder().id(a.getId()).activityId(a.getActivityBookingId()).code(a.getCode()).name(a.getName()).description(a.getDescription()).currency(a.getCurrency()).basePrice(a.getBasePrice()).originalPrice(a.getOriginalPrice()).packageGroup(a.getPackageGroup()).groupType(a.getGroupType()).departureType(a.getDepartureType()).details(readValue(a.getDetails(),new TypeReference<com.ds.goroute.dto.ActivityPackageDetails>(){},null)).minQuantity(a.getMinQuantity()).maxQuantity(a.getMaxQuantity()).inventoryType(a.getInventoryType()).units(readList(a.getUnits(),com.ds.goroute.dto.ActivityPackageUnit.class)).includedItems(readList(a.getIncludedItems(),String.class)).excludedItems(readList(a.getExcludedItems(),String.class)).requiredInformation(readList(a.getRequiredInformation(),String.class)).confirmationType(a.getConfirmationType()).voucherType(a.getVoucherType()).validityDays(a.getValidityDays()).attributes(readMap(a.getAttributes())).cancellationPolicy(readMap(a.getCancellationPolicy())).status(a.getStatus()).dataVersion(a.getDataVersion()).createdAt(a.getCreatedAt()).updatedAt(a.getUpdatedAt()).build();}
 private ActivitySlotResponse slotResponse(ActivitySlot s){return ActivitySlotResponse.builder().id(s.getId()).packageId(s.getPackageId()).startsAt(s.getStartsAt()).endsAt(s.getEndsAt()).timezone(s.getTimezone()).capacity(s.getCapacity()).reservedQuantity(s.getReservedQuantity()).soldQuantity(s.getSoldQuantity()).blockedQuantity(s.getBlockedQuantity()).availableQuantity(s.getAvailableQuantity()).bookingCutoffMinutes(s.getBookingCutoffMinutes()).priceOverride(s.getPriceOverride()).unitPrices(readValue(s.getUnitPrices(),new TypeReference<Map<String,BigDecimal>>(){},Map.of())).allDay(s.getAllDay()).meetingPointOverride(s.getMeetingPointOverride()).status(s.getStatus()).dataVersion(s.getDataVersion()).createdAt(s.getCreatedAt()).updatedAt(s.getUpdatedAt()).build();}
 private ActivityOrderResponse orderResponse(ActivityOrder o){ActivityOrderItem i=repository.findOrderItem(o.getId()).orElse(null);ActivityOrderItemResponse ir=i==null?null:ActivityOrderItemResponse.builder().id(i.getId()).packageId(i.getPackageId()).packageName(i.getPackageName()).quantity(i.getQuantity()).unitPrice(i.getUnitPrice()).totalPrice(i.getTotalPrice()).unitSelections(readMap(i.getUnitSelections())).snapshot(readMap(i.getSnapshot())).build();return ActivityOrderResponse.builder().id(o.getId()).orderCode(o.getOrderCode()).userId(o.getUserId()).organizationId(o.getOrganizationId()).activityId(o.getActivityBookingId()).activityTitle(o.getActivityTitle()).slotId(o.getSlotId()).slotStartsAt(o.getSlotStartsAt()).slotEndsAt(o.getSlotEndsAt()).slotTimezone(o.getSlotTimezone()).participants(readValue(o.getParticipantInfo(),new TypeReference<List<Map<String,Object>>>(){},List.of())).contactInfo(readMap(o.getContactInfo())).specialRequests(o.getSpecialRequests()).redemptionStatus(o.getRedemptionStatus()).redeemedAt(o.getRedeemedAt()).redeemedBy(o.getRedeemedBy()).guestCharged(o.getGuestCharged()).voucherType(voucherTypeOf(o)).holdExpiresAt(o.getHoldExpiresAt()).currency(o.getCurrency()).subtotalAmount(o.getSubtotalAmount()).taxAmount(o.getTaxAmount()).feeAmount(o.getFeeAmount()).discountAmount(o.getDiscountAmount()).totalAmount(o.getTotalAmount()).orderStatus(o.getOrderStatus()).paymentStatus(o.getPaymentStatus()).voucherCode(o.getVoucherCode()).snapshot(readMap(o.getSnapshot())).dataVersion(o.getDataVersion()).item(ir).createdAt(o.getCreatedAt()).updatedAt(o.getUpdatedAt()).build();}
 private MarketplaceActivityProduct product(UUID id){return repository.findProduct(id).orElseThrow(()->notFound("Activity not found"));}private MarketplaceActivityProduct publicProduct(UUID id){return repository.findPublicProduct(id).orElseThrow(()->notFound("Activity not found"));}private ActivityPackage pack(UUID id){return repository.findPackage(id).orElseThrow(()->notFound("Package not found"));}private ActivitySlot slot(UUID id){return repository.findSlot(id).orElseThrow(()->notFound("Slot not found"));}private ActivityOrder order(UUID id){return repository.findOrder(id).orElseThrow(()->notFound("Activity order not found"));}
 private boolean organizationBookable(UUID organizationId){return organizationId!=null&&organizationRepository.findById(organizationId).map(o->OrganizationOperationalStatus.ENABLED.name().equals(o.getOperationalStatus())&&OrganizationVerificationStatus.VERIFIED.name().equals(o.getVerificationStatus())).orElse(false);}
 private ActivitySlotResponse createSlot(UUID actor,ActivityPackage pack,MarketplaceActivityProduct product,UpsertActivitySlotRequest r){validateSlot(pack,r);LocalDateTime n=LocalDateTime.now();if(!r.getStartsAt().isAfter(n))throw bad("Slot start must be in the future");ActivitySlot s=ActivitySlot.builder().id(UUID.randomUUID()).packageId(pack.getId()).startsAt(r.getStartsAt()).endsAt(r.getEndsAt()).timezone(r.getTimezone()).capacity(r.getCapacity()).reservedQuantity(0).soldQuantity(0).blockedQuantity(r.getBlockedQuantity()).bookingCutoffMinutes(r.getBookingCutoffMinutes()).priceOverride(r.getPriceOverride()).unitPrices(json(map(r.getUnitPrices()))).allDay(Boolean.TRUE.equals(r.getAllDay())).meetingPointOverride(clean(r.getMeetingPointOverride())).status(enumNameOrDefault(r.getStatus(),MarketplaceSlotStatus.ENABLED)).dataVersion(1L).updatedBy(actor).createdAt(n).updatedAt(n).build();repository.insertSlot(s);history.record(product.getOrganizationId(),"ACTIVITY_SLOT",s.getId(),"CREATED",s,List.of(),actor,"USER",null);return slotResponse(repository.findSlot(s.getId()).orElse(s));}
 private void rejectExistingSlotStarts(UUID packageId,List<UpsertActivitySlotRequest> requests){Set<LocalDateTime> starts=requests.stream().map(UpsertActivitySlotRequest::getStartsAt).collect(java.util.stream.Collectors.toSet());if(starts.size()!=requests.size())throw bad("Duplicate slot start time in request");LocalDateTime first=starts.stream().min(LocalDateTime::compareTo).orElseThrow(()->bad("At least one slot is required"));LocalDateTime last=starts.stream().max(LocalDateTime::compareTo).orElse(first);boolean exists=repository.findSlots(packageId,first,true).stream().map(ActivitySlot::getStartsAt).filter(start->!start.isAfter(last)).anyMatch(starts::contains);if(exists)throw conflict("A slot already exists at one of the selected start times");}
 private void validatePackage(UpsertActivityPackageRequest r){currency(r.getCurrency());if(r.getInventoryType()!=null&&r.getInventoryType()!=ActivityInventoryType.SLOT)throw bad("Only SLOT inventory is supported until daily, resource, and unlimited capacity are implemented end-to-end");if(r.getMaxQuantity()!=null&&r.getMaxQuantity()<r.getMinQuantity())throw bad("maxQuantity must be >= minQuantity");if(r.getOriginalPrice()!=null&&r.getBasePrice()!=null&&r.getOriginalPrice().compareTo(r.getBasePrice())<0)throw bad("originalPrice must be >= basePrice");Set<String> codes=new HashSet<>();for(ActivityPackageUnit unit:list(r.getUnits())){String code=unit.getCode().trim().toUpperCase(Locale.ROOT);if(!codes.add(code))throw bad("Duplicate package unit code: "+code);if(unit.getMaxAge()!=null&&unit.getMinAge()!=null&&unit.getMaxAge()<unit.getMinAge())throw bad(code+" maxAge must be >= minAge");if(unit.getMaxQuantity()!=null&&unit.getMinQuantity()!=null&&unit.getMaxQuantity()<unit.getMinQuantity())throw bad(code+" maxQuantity must be >= minQuantity");}}
 private void validateSlot(ActivityPackage pack,UpsertActivitySlotRequest r){try{ZoneId.of(r.getTimezone());}catch(Exception e){throw bad("Invalid IANA timezone");}if(r.getEndsAt()!=null&&!r.getEndsAt().isAfter(r.getStartsAt()))throw bad("endsAt must be after startsAt");if(r.getBlockedQuantity()>r.getCapacity())throw bad("blockedQuantity exceeds capacity");if(r.getUnitPrices()!=null&&!r.getUnitPrices().isEmpty()){Set<String> codes=readList(pack.getUnits(),ActivityPackageUnit.class).stream().map(unit->unit.getCode().trim().toUpperCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet());for(String code:r.getUnitPrices().keySet())if(!codes.contains(code.trim().toUpperCase(Locale.ROOT)))throw bad("Unknown package unit price code: "+code);}}
 private void currency(String v){try{Currency.getInstance(v);}catch(Exception e){throw bad("Invalid currency");}}
 private long requiredVersion(Long value){if(value==null||value<1)throw bad("expectedVersion is required for an update");return value;}private void optimistic(int n,String x){if(n!=1)throw conflict(x+" was changed or violates allocated inventory");}private String orderCode(){return "ACT-"+LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)+"-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();}private String code(String s){return s.trim().toUpperCase(Locale.ROOT);}private String clean(String s){return s==null||s.isBlank()?null:s.trim();}private String enumNameOrDefault(Enum<?> value,Enum<?> fallback){return value==null?fallback.name():value.name();}private String enumNameOrDefault(Enum<?> value,String fallback){return value==null?fallback:value.name();}private String partnerPublicationStatus(MarketplacePublicationStatus value,String fallback){String status=enumNameOrDefault(value,fallback);if(MarketplacePublicationStatus.SUSPENDED.name().equals(status))throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,"Only an admin can suspend an activity");return status;}private <T>List<T> list(List<T> v){return v==null?List.of():v;}private <K,V>Map<K,V> map(Map<K,V> v){return v==null?Map.of():v;}
 private String actorType(UUID actor){return actor!=null&&adminMapper.hasAnyRole(actor)?"ADMIN":"USER";}
 private void notifyPartnerRequest(UUID organizationId,UUID productId,UUID orderId,String code,LocalDateTime deadline,UUID guestId){Map<String,Object> data=Map.of("orderId",orderId.toString(),"bookingCode",code,"deepLink","/partner/bookings?order="+orderId);authorization.notificationRecipients(organizationId,"ACTIVITY",productId,"ORDER_READ",guestId).forEach(userId->notificationService.createNotification(userId,null,NotificationType.MARKETPLACE_BOOKING_REQUEST,"New activity request",code+" needs a response before "+deadline,data,guestId));}
 private void notifyPartnerGuestCancelled(ActivityOrder order,UUID guestId){Map<String,Object> data=Map.of("orderId",order.getId().toString(),"bookingCode",order.getOrderCode(),"deepLink","/partner/bookings?order="+order.getId());authorization.notificationRecipients(order.getOrganizationId(),"ACTIVITY",order.getActivityBookingId(),"ORDER_READ",guestId).forEach(userId->notificationService.createNotification(userId,null,NotificationType.MARKETPLACE_BOOKING_CANCELLED_BY_GUEST,"Guest cancelled an order",order.getOrderCode()+" was cancelled by the guest",data,guestId));}
 private void notifyReviewInvite(ActivityOrder order,UUID actor){Map<String,Object> data=Map.of("orderId",order.getId().toString(),"activityOrderId",order.getId().toString(),"bookingCode",order.getOrderCode(),"deepLink","/marketplace/orders/activity/"+order.getId());notificationService.createNotification(order.getUserId(),null,NotificationType.MARKETPLACE_REVIEW_INVITE,"How was your experience?",order.getOrderCode()+" is complete — share a review",data,actor);}
 private void notifyGuestLifecycle(ActivityOrder order,MarketplaceBookingStatus target,String reason,UUID actor){NotificationType type=target==MarketplaceBookingStatus.EXPIRED?NotificationType.MARKETPLACE_BOOKING_EXPIRED:NotificationType.MARKETPLACE_BOOKING_UPDATED;String label=target.name().toLowerCase(Locale.ROOT).replace('_',' ');Map<String,Object> data=Map.of("orderId",order.getId().toString(),"bookingCode",order.getOrderCode(),"statusLabel",label,"status",target.name(),"deepLink","/marketplace/orders");notificationService.createNotification(order.getUserId(),null,type,"Order "+label,order.getOrderCode()+(reason==null?"":": "+reason),data,actor);}
 private void notifyPartnerConfirmed(UUID organizationId,UUID productId,UUID orderId,String code,UUID guestId){Map<String,Object> data=Map.of("orderId",orderId.toString(),"bookingCode",code,"deepLink","/partner/bookings?order="+orderId);authorization.notificationRecipients(organizationId,"ACTIVITY",productId,"ORDER_READ",guestId).forEach(userId->notificationService.createNotification(userId,null,NotificationType.MARKETPLACE_BOOKING_CONFIRMED,"New confirmed activity booking",code+" was confirmed instantly",data,guestId));}
 private LocalDateTime partnerConfirmationDeadline(LocalDateTime now){if(partnerConfirmationSlaMinutes<15||partnerConfirmationSlaMinutes>24*60)throw new IllegalStateException("goroute.marketplace.partner-confirmation-sla-minutes must be between 15 and 1440");return now.plusMinutes(partnerConfirmationSlaMinutes);}
 private void notifyGuestStatus(ActivityOrder order,MarketplaceBookingStatus target,String reason,UUID actor){notificationService.createNotification(order.getUserId(),null,target==MarketplaceBookingStatus.CONFIRMED?NotificationType.MARKETPLACE_BOOKING_CONFIRMED:NotificationType.MARKETPLACE_BOOKING_DECLINED,target==MarketplaceBookingStatus.CONFIRMED?"Activity request confirmed":"Activity request declined",order.getOrderCode()+(reason==null?"":": "+reason),Map.of("orderId",order.getId().toString(),"deepLink","/marketplace/orders"),actor);}
 private String json(Object v){try{return objectMapper.writeValueAsString(v);}catch(JsonProcessingException e){throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,"Cannot serialize activity data");}}private Map<String,Object> readMap(String v){return readValue(v,new TypeReference<Map<String,Object>>(){},Map.of());}private <T>List<T> readList(String v,Class<T> c){if(v==null)return List.of();try{return objectMapper.readValue(v,objectMapper.getTypeFactory().constructCollectionType(List.class,c));}catch(Exception e){return List.of();}}private <T>T readValue(String v,TypeReference<T> t,T f){if(v==null)return f;try{return objectMapper.readValue(v,t);}catch(Exception e){return f;}}
 private Page page(int p,int s){int l=Math.min(Math.max(s,1),200);return new Page(l,Math.max(p,0)*l);}private BusinessException bad(String m){return new BusinessException(ErrorConstant.BAD_REQUEST,m);}private BusinessException conflict(String m){return new BusinessException(ErrorConstant.ALREADY_PROCESSED,m);}private BusinessException notFound(String m){return new BusinessException(ErrorConstant.NOT_FOUND,m);}private BusinessException forbidden(){return new BusinessException(ErrorConstant.FORBIDDEN_ERROR,"You cannot access this activity order");}private record Page(int limit,int offset){}
 record OrderPricing(int capacityQuantity,int saleQuantity,BigDecimal total,Map<String,Object> selections){}
}
