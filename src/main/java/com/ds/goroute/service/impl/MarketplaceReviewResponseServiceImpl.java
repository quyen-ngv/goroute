package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpsertMarketplaceReviewResponseRequest;
import com.ds.goroute.dto.response.MarketplaceReviewViewResponse;
import com.ds.goroute.entity.MarketplaceReviewView;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.MarketplaceReviewResponseRepository;
import com.ds.goroute.service.MarketplaceHistoryService;
import com.ds.goroute.service.MarketplaceReviewResponseService;
import com.ds.goroute.service.PartnerAuthorizationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceReviewResponseServiceImpl implements MarketplaceReviewResponseService {
    private final MarketplaceReviewResponseRepository repository;
    private final PartnerAuthorizationService authorizationService;
    private final MarketplaceHistoryService historyService;
    private final ObjectMapper objectMapper;

    @Override
    public List<MarketplaceReviewViewResponse> partnerList(UUID actorUserId, UUID organizationId, int page, int size) {
        authorizationService.requireOrganization(organizationId, actorUserId);
        Page request = page(page, size);
        return repository.findByOrganization(organizationId, request.limit, request.offset).stream()
                .filter(review -> canRespond(organizationId, actorUserId, review))
                .map(this::response)
                .toList();
    }

    @Override
    @Transactional
    public MarketplaceReviewViewResponse respond(UUID actorUserId, UUID organizationId, UUID reviewId,
                                                  UpsertMarketplaceReviewResponseRequest request) {
        authorizationService.requireOrganization(organizationId, actorUserId);
        MarketplaceReviewView review = repository.find(reviewId, organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND));
        requireReviewScope(organizationId, actorUserId, review);

        LocalDateTime now = LocalDateTime.now();
        if (review.getResponseId() == null) {
            repository.insert(UUID.randomUUID(), reviewId, organizationId, actorUserId,
                    request.getResponseText().trim(), request.getStatus(), now);
        } else {
            long expectedVersion = request.getExpectedVersion() == null
                    ? review.getResponseVersion() : request.getExpectedVersion();
            if (repository.update(reviewId, organizationId, expectedVersion, actorUserId,
                    request.getResponseText().trim(), request.getStatus(), now) != 1) {
                throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                        "Review response was changed; reload and retry");
            }
        }

        MarketplaceReviewView saved = repository.find(reviewId, organizationId).orElseThrow();
        historyService.record(organizationId, "REVIEW_RESPONSE", saved.getResponseId(),
                review.getResponseId() == null ? "CREATED" : "UPDATED", saved,
                List.of("responseText", "status"), actorUserId, "USER", null);
        return response(saved);
    }

    @Override
    public List<MarketplaceReviewViewResponse> adminList(String query, int page, int size) {
        Page request = page(page, size);
        return repository.findAdmin(clean(query), request.limit, request.offset).stream()
                .map(this::response)
                .toList();
    }

    @Override
    @Transactional
    public MarketplaceReviewViewResponse adminRespond(UUID actorUserId, UUID organizationId, UUID reviewId,
            UpsertMarketplaceReviewResponseRequest request) {
        MarketplaceReviewView review = repository.find(reviewId, organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now();
        if (review.getResponseId() == null) repository.insert(UUID.randomUUID(), reviewId, organizationId, actorUserId,
                request.getResponseText().trim(), request.getStatus(), now);
        else {
            long expected = request.getExpectedVersion() == null ? review.getResponseVersion() : request.getExpectedVersion();
            if (repository.update(reviewId, organizationId, expected, actorUserId, request.getResponseText().trim(),
                    request.getStatus(), now) != 1) throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "Review response was changed; reload and retry");
        }
        MarketplaceReviewView saved = repository.find(reviewId, organizationId).orElseThrow();
        historyService.record(organizationId,"REVIEW_RESPONSE",saved.getResponseId(),"ADMIN_UPDATED",saved,
                List.of("responseText","status"),actorUserId,"ADMIN",null); return response(saved);
    }

    @Override
    @Transactional
    public MarketplaceReviewViewResponse adminHide(UUID actorUserId, UUID organizationId, UUID reviewId, String reason) {
        MarketplaceReviewView review = repository.find(reviewId, organizationId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.REVIEW_NOT_FOUND));
        if (review.getResponseId() == null) throw new BusinessException(ErrorConstant.NOT_FOUND,"Partner response not found");
        if (repository.update(reviewId,organizationId,review.getResponseVersion(),actorUserId,review.getResponseText(),"HIDDEN",LocalDateTime.now())!=1)
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,"Review response was changed; reload and retry");
        MarketplaceReviewView saved=repository.find(reviewId,organizationId).orElseThrow();historyService.record(organizationId,
                "REVIEW_RESPONSE",saved.getResponseId(),"ADMIN_HIDDEN",saved,List.of("status"),actorUserId,"ADMIN",clean(reason));return response(saved);
    }

    private boolean canRespond(UUID organizationId, UUID actorUserId, MarketplaceReviewView review) {
        UUID resourceId = review.getHotelId() != null ? review.getHotelId() : review.getActivityBookingId();
        String resourceType = review.getHotelId() != null ? "HOTEL" : "ACTIVITY";
        return resourceId != null && authorizationService.hasResourcePermission(
                organizationId, actorUserId, resourceType, resourceId, "REVIEW_RESPOND");
    }

    private void requireReviewScope(UUID organizationId, UUID actorUserId, MarketplaceReviewView review) {
        if (review.getHotelId() != null) {
            authorizationService.requireResourcePermission(
                    organizationId, actorUserId, "HOTEL", review.getHotelId(), "REVIEW_RESPOND");
            return;
        }
        if (review.getActivityBookingId() != null) {
            authorizationService.requireResourcePermission(
                    organizationId, actorUserId, "ACTIVITY", review.getActivityBookingId(), "REVIEW_RESPOND");
            return;
        }
        throw new BusinessException(ErrorConstant.FORBIDDEN_ERROR,
                "Review is not associated with a resource owned by this organization");
    }

    private MarketplaceReviewViewResponse response(MarketplaceReviewView view) {
        return MarketplaceReviewViewResponse.builder()
                .reviewId(view.getReviewId())
                .reviewerUserId(view.getReviewerUserId())
                .reviewerName(view.getReviewerName())
                .placeId(view.getPlaceId())
                .hotelId(view.getHotelId())
                .activityId(view.getActivityBookingId())
                .subjectName(view.getSubjectName())
                .overallRating(view.getOverallRating())
                .reviewText(view.getReviewText())
                .photos(photos(view.getPhotos()))
                .reviewCreatedAt(view.getReviewCreatedAt())
                .responseId(view.getResponseId())
                .organizationId(view.getOrganizationId())
                .responderUserId(view.getResponderUserId())
                .responderName(view.getResponderName())
                .responseText(view.getResponseText())
                .responseStatus(view.getResponseStatus())
                .responseVersion(view.getResponseVersion())
                .responseCreatedAt(view.getResponseCreatedAt())
                .responseUpdatedAt(view.getResponseUpdatedAt())
                .build();
    }

    private List<String> photos(String json) {
        if (json == null) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private Page page(int page, int size) {
        int limit = Math.min(Math.max(size, 1), 200);
        return new Page(limit, Math.max(page, 0) * limit);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record Page(int limit, int offset) {}
}
