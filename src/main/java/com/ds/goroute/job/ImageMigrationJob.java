package com.ds.goroute.job;

import com.ds.goroute.entity.*;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ImageMigrationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class ImageMigrationJob {

    private final ImageMigrationService imageMigrationService;
    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final ActivityRepository activityRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final FoodRepository foodRepository;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;

    /**
     * Run full image migration for all tables
     */
    @Async
    public void runFullMigration() {
        log.info("=== Starting Full Image Migration ===");
        long startTime = System.currentTimeMillis();
        
        try {
            migratePlaces();
            migratePlaceReviews();
            migrateActivities();
            migrateActivityBookings();
            migrateFoods();
            migrateExpenses();
            
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("=== Full Image Migration Completed in {}s ===", duration);
            
        } catch (Exception e) {
            log.error("Full migration failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Migrate Place images
     */
    public void migratePlaces() {
        log.info("--- Migrating Place images ---");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        List<Place> places = placeRepository.findAll();
        log.info("Found {} places to process", places.size());
        
        for (Place place : places) {
            try {
                boolean updated = false;
                String targetPath = "places/" + place.getPlaceId() + "/";
                
                // Migrate thumbnail
                if (isExternalUrl(place.getThumbnail())) {
                    String newUrl = imageMigrationService.migrateImage(place.getThumbnail(), targetPath);
                    if (newUrl != null) {
                        place.setThumbnail(newUrl);
                        updated = true;
                    }
                }
                
                // Migrate images JSON
                if (place.getImages() != null && !place.getImages().equals("[]")) {
                    String newImages = imageMigrationService.migrateImagesJson(place.getImages(), targetPath);
                    if (!newImages.equals(place.getImages())) {
                        place.setImages(newImages);
                        updated = true;
                    }
                }
                
                if (updated) {
                    placeRepository.update(place);
                    success.incrementAndGet();
                    log.debug("Migrated place: {}", place.getPlaceId());
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate place {}: {}", place.getPlaceId(), e.getMessage());
            }
        }
        
        log.info("Place migration: {} success, {} failed", success.get(), failed.get());
    }

    /**
     * Migrate PlaceReview images
     */
    public void migratePlaceReviews() {
        log.info("--- Migrating PlaceReview images ---");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        List<PlaceReview> reviews = placeReviewRepository.findAll();
        log.info("Found {} reviews to process", reviews.size());
        
        for (PlaceReview review : reviews) {
            try {
                boolean updated = false;
                String targetPath = "reviews/" + review.getId() + "/";
                
                // Migrate profilePicture
                if (isExternalUrl(review.getProfilePicture())) {
                    String newUrl = imageMigrationService.migrateImage(review.getProfilePicture(), targetPath);
                    if (newUrl != null) {
                        review.setProfilePicture(newUrl);
                        updated = true;
                    }
                }
                
                // Migrate images JSON array
                if (review.getImages() != null && !review.getImages().equals("[]")) {
                    String newImages = migrateReviewImagesArray(review.getImages(), targetPath);
                    if (!newImages.equals(review.getImages())) {
                        review.setImages(newImages);
                        updated = true;
                    }
                }
                
                if (updated) {
                    placeReviewRepository.update(review);
                    success.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate review {}: {}", review.getId(), e.getMessage());
            }
        }
        
        log.info("Review migration: {} success, {} failed", success.get(), failed.get());
    }

    /**
     * Migrate Activity images
     */
    public void migrateActivities() {
        log.info("--- Migrating Activity images ---");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        List<Activity> activities = activityRepository.findAll();
        log.info("Found {} activities to process", activities.size());
        
        for (Activity activity : activities) {
            try {
                if (isExternalUrl(activity.getPhotoUrl())) {
                    String targetPath = "activities/" + activity.getId() + "/";
                    String newUrl = imageMigrationService.migrateImage(activity.getPhotoUrl(), targetPath);
                    
                    if (newUrl != null) {
                        activity.setPhotoUrl(newUrl);
                        activityRepository.update(activity);
                        success.incrementAndGet();
                    }
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate activity {}: {}", activity.getId(), e.getMessage());
            }
        }
        
        log.info("Activity migration: {} success, {} failed", success.get(), failed.get());
    }

    /**
     * Migrate ActivityBooking images
     */
    public void migrateActivityBookings() {
        log.info("--- Migrating ActivityBooking images ---");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        List<ActivityBooking> bookings = activityBookingRepository.findAll();
        log.info("Found {} bookings to process", bookings.size());
        
        for (ActivityBooking booking : bookings) {
            try {
                boolean updated = false;
                String targetPath = "bookings/" + booking.getId() + "/";
                
                // Migrate thumbnail
                if (isExternalUrl(booking.getThumbnail())) {
                    String newUrl = imageMigrationService.migrateImage(booking.getThumbnail(), targetPath);
                    if (newUrl != null) {
                        booking.setThumbnail(newUrl);
                        updated = true;
                    }
                }
                
                // Migrate images JSON
                if (booking.getImages() != null && !booking.getImages().equals("[]")) {
                    String newImages = imageMigrationService.migrateImagesJson(booking.getImages(), targetPath);
                    if (!newImages.equals(booking.getImages())) {
                        booking.setImages(newImages);
                        updated = true;
                    }
                }
                
                // Migrate itinerary JSON (complex structure)
                if (booking.getItinerary() != null && !booking.getItinerary().equals("[]")) {
                    String newItinerary = migrateItineraryImages(booking.getItinerary(), targetPath + "itinerary/");
                    if (!newItinerary.equals(booking.getItinerary())) {
                        booking.setItinerary(newItinerary);
                        updated = true;
                    }
                }
                
                if (updated) {
                    activityBookingRepository.update(booking);
                    success.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate booking {}: {}", booking.getId(), e.getMessage());
            }
        }
        
        log.info("Booking migration: {} success, {} failed", success.get(), failed.get());
    }

    /**
     * Migrate Food images
     */
    public void migrateFoods() {
        log.info("--- Migrating Food images ---");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        List<Food> foods = foodRepository.findAll();
        log.info("Found {} foods to process", foods.size());
        
        for (Food food : foods) {
            try {
                if (isExternalUrl(food.getImageUrl())) {
                    String targetPath = "foods/" + food.getId() + "/";
                    String newUrl = imageMigrationService.migrateImage(food.getImageUrl(), targetPath);
                    
                    if (newUrl != null) {
                        food.setImageUrl(newUrl);
                        foodRepository.update(food);
                        success.incrementAndGet();
                    }
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate food {}: {}", food.getId(), e.getMessage());
            }
        }
        
        log.info("Food migration: {} success, {} failed", success.get(), failed.get());
    }

    /**
     * Migrate Expense images
     */
    public void migrateExpenses() {
        log.info("--- Migrating Expense images ---");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        
        List<Expense> expenses = expenseRepository.findAll();
        log.info("Found {} expenses to process", expenses.size());
        
        for (Expense expense : expenses) {
            try {
                boolean updated = false;
                String targetPath = "expenses/" + expense.getId() + "/";
                
                // Migrate receiptUrl
                if (isExternalUrl(expense.getReceiptUrl())) {
                    String newUrl = imageMigrationService.migrateImage(expense.getReceiptUrl(), targetPath);
                    if (newUrl != null) {
                        expense.setReceiptUrl(newUrl);
                        updated = true;
                    }
                }
                
                // Migrate photoUrls array
                if (expense.getPhotoUrls() != null && expense.getPhotoUrls().length > 0) {
                    List<String> urlList = Arrays.asList(expense.getPhotoUrls());
                    List<String> externalUrls = urlList.stream().filter(this::isExternalUrl).toList();
                    
                    if (!externalUrls.isEmpty()) {
                        Map<String, String> migratedUrls = imageMigrationService.migrateImages(externalUrls, targetPath);
                        
                        // Replace old URLs with new ones
                        String[] newPhotoUrls = urlList.stream()
                                .map(url -> migratedUrls.getOrDefault(url, url))
                                .toArray(String[]::new);
                        
                        expense.setPhotoUrls(newPhotoUrls);
                        updated = true;
                    }
                }
                
                if (updated) {
                    expenseRepository.update(expense);
                    success.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate expense {}: {}", expense.getId(), e.getMessage());
            }
        }
        
        log.info("Expense migration: {} success, {} failed", success.get(), failed.get());
    }

    // Helper methods

    private boolean isExternalUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        // Migrate all HTTP URLs except our own MinIO domain
        return url.startsWith("http") && !url.contains("onestudy.id.vn");
    }

    /**
     * Migrate review images array: ["url1", "url2"]
     */
    private String migrateReviewImagesArray(String imagesJson, String targetPath) {
        try {
            JsonNode rootNode = objectMapper.readTree(imagesJson);
            if (!rootNode.isArray()) {
                return imagesJson;
            }
            
            ArrayNode arrayNode = (ArrayNode) rootNode;
            List<String> imageUrls = new ArrayList<>();
            
            for (JsonNode node : arrayNode) {
                if (node.isTextual() && isExternalUrl(node.asText())) {
                    imageUrls.add(node.asText());
                }
            }
            
            if (imageUrls.isEmpty()) {
                return imagesJson;
            }
            
            Map<String, String> migratedUrls = imageMigrationService.migrateImages(imageUrls, targetPath);
            
            ArrayNode newArray = objectMapper.createArrayNode();
            for (JsonNode node : arrayNode) {
                if (node.isTextual()) {
                    String oldUrl = node.asText();
                    String newUrl = migratedUrls.getOrDefault(oldUrl, oldUrl);
                    newArray.add(newUrl);
                } else {
                    newArray.add(node);
                }
            }
            
            return objectMapper.writeValueAsString(newArray);
            
        } catch (Exception e) {
            log.error("Error migrating review images array: {}", e.getMessage());
            return imagesJson;
        }
    }

    /**
     * Migrate itinerary images: [{title, content, images[]}]
     */
    private String migrateItineraryImages(String itineraryJson, String targetPath) {
        try {
            JsonNode rootNode = objectMapper.readTree(itineraryJson);
            if (!rootNode.isArray()) {
                return itineraryJson;
            }
            
            ArrayNode arrayNode = (ArrayNode) rootNode;
            ArrayNode newArray = objectMapper.createArrayNode();
            
            for (JsonNode itemNode : arrayNode) {
                ObjectNode newItem = ((ObjectNode) itemNode).deepCopy();
                
                if (itemNode.has("images") && itemNode.get("images").isArray()) {
                    ArrayNode imagesArray = (ArrayNode) itemNode.get("images");
                    List<String> imageUrls = new ArrayList<>();
                    
                    for (JsonNode imgNode : imagesArray) {
                        if (imgNode.isTextual() && isExternalUrl(imgNode.asText())) {
                            imageUrls.add(imgNode.asText());
                        }
                    }
                    
                    if (!imageUrls.isEmpty()) {
                        Map<String, String> migratedUrls = imageMigrationService.migrateImages(imageUrls, targetPath);
                        
                        ArrayNode newImagesArray = objectMapper.createArrayNode();
                        for (JsonNode imgNode : imagesArray) {
                            if (imgNode.isTextual()) {
                                String oldUrl = imgNode.asText();
                                String newUrl = migratedUrls.getOrDefault(oldUrl, oldUrl);
                                newImagesArray.add(newUrl);
                            } else {
                                newImagesArray.add(imgNode);
                            }
                        }
                        
                        newItem.set("images", newImagesArray);
                    }
                }
                
                newArray.add(newItem);
            }
            
            return objectMapper.writeValueAsString(newArray);
            
        } catch (Exception e) {
            log.error("Error migrating itinerary images: {}", e.getMessage());
            return itineraryJson;
        }
    }
}
