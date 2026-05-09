package com.ds.goroute.service.impl;

import com.ds.goroute.dto.request.ImportPlaceRequest;
import com.ds.goroute.dto.request.UpdatePlaceRequest;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PlaceAboutDto;
import com.ds.goroute.dto.response.PlaceImagesDto;
import com.ds.goroute.dto.response.PlaceResponse;
import com.ds.goroute.dto.response.PlaceReviewResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceReview;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.PlaceReviewRepository;
import com.ds.goroute.service.PlaceService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaceServiceImpl implements PlaceService {
    
    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final ObjectMapper objectMapper;
    
    @Override
    @Transactional
    public PlaceResponse importPlace(ImportPlaceRequest request) {
        log.info("Importing place: {}", request.getPlaceId());
        
        // Check if place already exists
        Place existingPlace = placeRepository.findByPlaceId(request.getPlaceId());
        if (existingPlace != null) {
            log.info("Place already exists, updating: {}", request.getPlaceId());
            return updateExistingPlace(existingPlace, request);
        }
        
        // Create new place
        Place place = buildPlaceFromRequest(request);
        placeRepository.insert(place);
        
        // Import reviews if provided
        if (request.getUserReviews() != null && !request.getUserReviews().isEmpty()) {
            importReviews(place.getId(), request.getUserReviews());
        }
        
        return toPlaceResponse(place);
    }
    
    @Override
    @Transactional
    public List<PlaceResponse> importPlaces(List<ImportPlaceRequest> requests) {
        log.info("Importing {} places", requests.size());
        return requests.stream()
                .map(this::importPlace)
                .collect(Collectors.toList());
    }
    
    @Override
    public PlaceResponse getPlaceById(UUID id) {
        Place place = placeRepository.findById(id);
        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        return toPlaceResponse(place);
    }
    
    @Override
    public PlaceResponse getPlaceByGoogleId(String placeId) {
        Place place = placeRepository.findByPlaceId(placeId);
        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        return toPlaceResponse(place);
    }
    
    @Override
    public List<PlaceResponse> searchPlaces(String keyword, BigDecimal latitude, BigDecimal longitude, 
                                           BigDecimal radius, String category, BigDecimal minRating, int page, int size) {
        int offset = page * size;
        List<Place> places = placeRepository.findNearby(keyword, latitude, longitude, radius, category, minRating, size, offset);
        return places.stream()
                .map(this::toPlaceResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<PlaceReviewResponse> getPlaceReviews(UUID placeId) {
        List<PlaceReview> reviews = placeReviewRepository.findByPlaceId(placeId);
        return reviews.stream()
                .map(this::toReviewResponse)
                .collect(Collectors.toList());
    }
    
    @Override
    @Transactional
    public void deletePlace(UUID id) {
        Place place = placeRepository.findById(id);
        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        
        // Delete reviews first
        placeReviewRepository.deleteByPlaceId(id);
        
        // Delete place
        placeRepository.delete(id);
        log.info("Deleted place: {}", id);
    }
    
    @Override
    @Transactional
    public PlaceResponse updatePlace(UUID id, UpdatePlaceRequest request) {
        Place place = placeRepository.findById(id);
        if (place == null) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        
        // Update all fields
        place.setTitle(request.getTitle());
        place.setCategory(request.getCategory());
        place.setAddress(request.getAddress());
        place.setLatitude(request.getLatitude());
        place.setLongitude(request.getLongitude());
        place.setPlusCode(request.getPlusCode());
        place.setTimezone(request.getTimezone());
        place.setPhone(request.getPhone());
        place.setWebsite(request.getWebsite());
        place.setGoogleMapsLink(request.getGoogleMapsLink());
        place.setReviewCount(request.getReviewCount());
        place.setReviewRating(request.getReviewRating());
        place.setReviewsPerRating(request.getReviewsPerRating());
        place.setThumbnail(request.getThumbnail());
        place.setImages(request.getImages());
        place.setDescriptions(request.getDescriptions());
        place.setStatus(request.getStatus());
        place.setPriceRange(request.getPriceRange());
        place.setOpenHours(request.getOpenHours());
        place.setPopularTimes(request.getPopularTimes());
        place.setReservations(request.getReservations());
        place.setOrderOnline(request.getOrderOnline());
        place.setMenu(request.getMenu());
        place.setCompleteAddress(request.getCompleteAddress());
        place.setAbout(request.getAbout());
        place.setOwner(request.getOwner());
        place.setEmails(request.getEmails());
        place.setUpdatedAt(LocalDateTime.now());
        
        placeRepository.update(place);
        log.info("Updated place: {}", id);
        
        return toPlaceResponse(place);
    }
    
    // Helper methods
    
    private PlaceResponse updateExistingPlace(Place existingPlace, ImportPlaceRequest request) {
        Place updated = buildPlaceFromRequest(request);
        updated.setId(existingPlace.getId());
        updated.setCreatedAt(existingPlace.getCreatedAt());
        updated.setUpdatedAt(LocalDateTime.now());
        
        placeRepository.update(updated);
        
        // Update reviews
        if (request.getUserReviews() != null && !request.getUserReviews().isEmpty()) {
            placeReviewRepository.deleteByPlaceId(existingPlace.getId());
            importReviews(existingPlace.getId(), request.getUserReviews());
        }
        
        return toPlaceResponse(updated);
    }
    
    private Place buildPlaceFromRequest(ImportPlaceRequest request) {
        return Place.builder()
                .id(UUID.randomUUID())
                .placeId(request.getPlaceId())
                .cid(request.getCid())
                .dataId(request.getDataId())
                .title(request.getTitle())
                .category(request.getCategory())
                .address(request.getAddress())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .plusCode(request.getPlusCode())
                .timezone(request.getTimezone())
                .phone(request.getPhone())
                .website(request.getWebsite())
                .googleMapsLink(request.getGoogleMapsLink())
                .reviewCount(request.getReviewCount())
                .reviewRating(request.getReviewRating())
                .reviewsPerRating(request.getReviewsPerRating())
                .thumbnail(request.getThumbnail())
                .images(request.getImages())
                .descriptions(request.getDescriptions())
                .status(request.getStatus())
                .priceRange(request.getPriceRange())
                .openHours(request.getOpenHours())
                .popularTimes(request.getPopularTimes())
                .reservations(request.getReservations())
                .orderOnline(request.getOrderOnline())
                .menu(request.getMenu())
                .completeAddress(request.getCompleteAddress())
                .about(request.getAbout())
                .owner(request.getOwner())
                .emails(request.getEmails())
                .rawData(request.getRawData())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
    
    private void importReviews(UUID placeId, String reviewsJson) {
        try {
            JsonNode reviewsNode = objectMapper.readTree(reviewsJson);
            if (!reviewsNode.isArray()) {
                return;
            }
            
            List<PlaceReview> reviews = new ArrayList<>();
            
            for (JsonNode reviewNode : reviewsNode) {
                try {
                    Integer rating = null;
                    if (reviewNode.has("rating") && !reviewNode.get("rating").isNull()) {
                        int ratingValue = reviewNode.get("rating").asInt();
                        if (ratingValue >= 1 && ratingValue <= 5) {
                            rating = ratingValue;
                        }
                    }
                    
                    PlaceReview review = PlaceReview.builder()
                            .id(UUID.randomUUID())
                            .placeId(placeId)
                            .reviewerName(getTextValue(reviewNode, "name"))
                            .profilePicture(getTextValue(reviewNode, "profilePicture"))
                            .rating(rating)
                            .description(getTextValue(reviewNode, "description"))
                            .reviewDate(parseReviewDate(getTextValue(reviewNode, "when")))
                            .images(reviewNode.has("images") ? reviewNode.get("images").toString() : null)
                            .createdAt(LocalDateTime.now())
                            .build();
                    
                    reviews.add(review);
                } catch (Exception e) {
                    log.error("Error parsing review: {}", e.getMessage());
                }
            }
            
            if (!reviews.isEmpty()) {
                placeReviewRepository.insertBatch(reviews);
                log.info("Imported {} reviews for place {}", reviews.size(), placeId);
            }
        } catch (Exception e) {
            log.error("Error parsing reviews JSON: {}", e.getMessage());
        }
    }
    
    private String getTextValue(JsonNode node, String fieldName) {
        return node.has(fieldName) && !node.get(fieldName).isNull() 
                ? node.get(fieldName).asText() 
                : null;
    }
    
    private LocalDate parseReviewDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }
        
        try {
            // Try format: "2025-6-1"
            return LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-M-d"));
        } catch (Exception e) {
            log.warn("Could not parse review date: {}", dateStr);
            return null;
        }
    }
    
    private PlaceResponse toPlaceResponse(Place place) {
        return PlaceResponse.builder()
                .id(place.getId())
                .placeId(place.getPlaceId())
                .title(place.getTitle())
                .category(place.getCategory())
                .address(place.getAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .phone(place.getPhone())
                .website(place.getWebsite())
                .googleMapsLink(place.getGoogleMapsLink())
                .reviewCount(place.getReviewCount())
                .reviewRating(place.getReviewRating())
                .reviewsPerRating(parseJsonToMap(place.getReviewsPerRating()))
                .thumbnail(place.getThumbnail())
                .images(parseJsonToList(place.getImages(), PlaceImagesDto.class))
                .descriptions(place.getDescriptions())
                .priceRange(place.getPriceRange())
                .openHours(parseJsonToMapOfList(place.getOpenHours()))
                .popularTimes(parseJsonToMapOfMap(place.getPopularTimes()))
                .about(parseJsonToList(place.getAbout(), PlaceAboutDto.class))
                .distance(place.getDistance())
                .createdAt(place.getCreatedAt())
                .updatedAt(place.getUpdatedAt())
                .build();
    }
    
    private Map<String, Integer> parseJsonToMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Integer>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map: {}", e.getMessage());
            return null;
        }
    }
    
    private <T> List<T> parseJsonToList(String jsonString, Class<T> clazz) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
        } catch (Exception e) {
            log.warn("Failed to parse JSON to List: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, List<String>> parseJsonToMapOfList(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<String>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map<String, List<String>>: {}", e.getMessage());
            return null;
        }
    }
    
    private Map<String, Map<String, Integer>> parseJsonToMapOfMap(String jsonString) {
        if (jsonString == null || jsonString.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, 
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Map<String, Integer>>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse JSON to Map<String, Map<String, Integer>>: {}", e.getMessage());
            return null;
        }
    }
    
    private PlaceReviewResponse toReviewResponse(PlaceReview review) {
        return PlaceReviewResponse.builder()
                .id(review.getId())
                .placeId(review.getPlaceId())
                .reviewerName(review.getReviewerName())
                .profilePicture(review.getProfilePicture())
                .rating(review.getRating())
                .description(review.getDescription())
                .reviewDate(review.getReviewDate())
                .images(review.getImages())
                .build();
    }
}