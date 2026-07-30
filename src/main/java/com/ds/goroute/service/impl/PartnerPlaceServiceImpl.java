package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpsertPartnerPlaceRequest;
import com.ds.goroute.dto.response.PartnerPlaceResponse;
import com.ds.goroute.entity.PartnerPlace;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PartnerPlaceRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.ds.goroute.service.PartnerPlaceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerPlaceServiceImpl implements PartnerPlaceService {
    private final PartnerPlaceRepository repository;
    private final PlaceRepository placeRepository;
    private final PartnerAuthorizationService authorizationService;
    private final MarketplaceHistoryService historyService;
    private final ObjectMapper objectMapper;

    @Override
    public List<PartnerPlaceResponse> list(UUID actor, UUID organizationId) {
        authorizationService.requirePermission(organizationId,actor,"HOTEL_READ");
        return repository.findByOrganization(organizationId).stream().map(value -> response(value,false)).toList();
    }

    @Override
    @Transactional
    public PartnerPlaceResponse create(UUID actor, UpsertPartnerPlaceRequest request) {
        authorizationService.requirePermission(request.getOrganizationId(),actor,"HOTEL_WRITE");
        if ((request.getLatitude()==null)!=(request.getLongitude()==null)) {
            throw badRequest("latitude and longitude must be provided together");
        }
        LocalDateTime now=LocalDateTime.now();
        Place candidate=request.getLatitude()==null ? null : placeRepository.findNearCoordinates(
                request.getLatitude(),request.getLongitude(),java.math.BigDecimal.valueOf(50));
        boolean attached=candidate!=null && isSamePlace(candidate,request);
        UUID placeId=attached ? candidate.getId() : UUID.randomUUID();
        PartnerPlace value=fromRequest(placeId,actor,request,now);
        if (!attached) repository.insertCanonical(value);
        UUID sourceId=repository.findSource(placeId,request.getOrganizationId()).orElse(null);
        if (sourceId==null) {
            UUID proposed=UUID.randomUUID();
            repository.insertSource(proposed,placeId,request.getOrganizationId(),now);
            sourceId=repository.findSource(placeId,request.getOrganizationId()).orElse(proposed);
        }
        repository.insertSnapshot(UUID.randomUUID(),sourceId,json(request),now);
        historyService.record(request.getOrganizationId(),"PLACE",placeId,
                attached ? "PARTNER_SOURCE_ATTACHED" : "CREATED",attached ? candidate : value,
                List.of(),actor,"USER",null);
        PartnerPlace saved=repository.find(placeId,request.getOrganizationId()).orElse(value);
        return response(saved,attached);
    }

    @Override
    @Transactional
    public PartnerPlaceResponse update(UUID actor,UUID placeId,UpsertPartnerPlaceRequest request) {
        authorizationService.requirePermission(request.getOrganizationId(),actor,"HOTEL_WRITE");
        PartnerPlace current=repository.find(placeId,request.getOrganizationId())
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        if (!"PARTNER".equals(current.getPrimarySourceType())) {
            throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,"Google/Admin canonical fields cannot be edited by partner");
        }
        long expected=request.getExpectedVersion()==null ? current.getDataVersion() : request.getExpectedVersion();
        PartnerPlace next=fromRequest(placeId,actor,request,current.getCreatedAt());
        next.setDataVersion(expected);
        next.setUpdatedAt(LocalDateTime.now());
        if (repository.updateCanonical(next)!=1) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,"Place was changed; reload and retry");
        }
        next.setDataVersion(expected+1);
        UUID sourceId=repository.findSource(placeId,request.getOrganizationId()).orElseThrow();
        repository.insertSnapshot(UUID.randomUUID(),sourceId,json(request),next.getUpdatedAt());
        historyService.record(request.getOrganizationId(),"PLACE",placeId,"UPDATED",next,
                List.of("CANONICAL_FIELDS"),actor,"USER",null);
        return response(repository.find(placeId,request.getOrganizationId()).orElse(next),false);
    }

    private PartnerPlace fromRequest(UUID id,UUID actor,UpsertPartnerPlaceRequest request,LocalDateTime createdAt) {
        if (request.getTimezone()!=null) {
            try { ZoneId.of(request.getTimezone()); }
            catch (Exception ex) { throw badRequest("Invalid IANA timezone"); }
        }
        return PartnerPlace.builder().id(id).organizationId(request.getOrganizationId())
                .title(request.getTitle().trim()).placeGroup(request.getPlaceGroup()).category(clean(request.getCategory()))
                .address(clean(request.getAddress())).latitude(request.getLatitude()).longitude(request.getLongitude())
                .timezone(clean(request.getTimezone())).phone(clean(request.getPhone())).website(clean(request.getWebsite()))
                .thumbnail(clean(request.getThumbnail())).images(json(request.getImages()==null?List.of():request.getImages()))
                .destinations(json(request.getDestinations()==null?List.of():request.getDestinations()))
                .descriptions(clean(request.getDescription())).lifecycleStatus("ACTIVE").primarySourceType("PARTNER")
                .dataVersion(1L).createdBy(actor).updatedBy(actor).createdAt(createdAt).updatedAt(LocalDateTime.now()).build();
    }

    private boolean isSamePlace(Place value,UpsertPartnerPlaceRequest request) {
        String title=normalize(value.getTitle());
        String requestedTitle=normalize(request.getTitle());
        String address=normalize(value.getAddress());
        String requestedAddress=normalize(request.getAddress());
        return title.equals(requestedTitle) || !address.isEmpty() && address.equals(requestedAddress);
    }

    private String normalize(String value) {
        return value==null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");
    }

    private PartnerPlaceResponse response(PartnerPlace value,boolean attached) {
        return PartnerPlaceResponse.builder().id(value.getId()).organizationId(value.getOrganizationId())
                .title(value.getTitle()).placeGroup(value.getPlaceGroup()).category(value.getCategory())
                .address(value.getAddress()).latitude(value.getLatitude()).longitude(value.getLongitude())
                .timezone(value.getTimezone()).phone(value.getPhone()).website(value.getWebsite()).thumbnail(value.getThumbnail())
                .images(readList(value.getImages())).destinations(readList(value.getDestinations())).description(value.getDescriptions())
                .lifecycleStatus(value.getLifecycleStatus()).primarySourceType(value.getPrimarySourceType())
                .dataVersion(value.getDataVersion()).attachedExisting(attached).createdAt(value.getCreatedAt()).updatedAt(value.getUpdatedAt()).build();
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,"Cannot serialize place source"); }
    }
    private List<String> readList(String value) {
        if (value==null) return List.of();
        try { return objectMapper.readValue(value,new TypeReference<List<String>>(){}); }
        catch (Exception ex) { return List.of(); }
    }
    private String clean(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private BusinessException badRequest(String message) { return new BusinessException(ErrorConstant.BAD_REQUEST,message); }
}
