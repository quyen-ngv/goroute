package com.ds.goroute.job;

import com.ds.goroute.entity.*;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ImageArchiveService;
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
public class ImageArchiveJob {

    private final ImageArchiveService imageArchiveService;
    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final ActivityRepository activityRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final FoodRepository foodRepository;
    private final ExpenseRepository expenseRepository;
    private final ObjectMapper objectMapper;

    private static final String RESOURCES_BASE = "resources/";
    
    // Controlled processing to avoid overwhelming server
    private static final int LOG_INTERVAL = 50; // Log every 50 records

    @Async
    public void runFullArchive() {
        log.info("🚀 === STARTING FULL IMAGE COMPRESSION & CLEANUP ===");
        log.info("📁 Target folder: {}", RESOURCES_BASE);
        log.info("🎯 70% size reduction + delete originals");
        
        long startTime = System.currentTimeMillis();
        
        try {
            log.info("1️⃣ Processing Places...");
            archivePlaces();
            
            log.info("2️⃣ Processing Place Reviews...");
            archivePlaceReviews();
            
            log.info("3️⃣ Processing Activities...");
            archiveActivities();
            
            log.info("4️⃣ Processing Activity Bookings...");
            archiveActivityBookings();
            
            log.info("5️⃣ Processing Foods...");
            archiveFoods();
            
            log.info("6️⃣ Processing Expenses...");
            archiveExpenses();
            
            long duration = (System.currentTimeMillis() - startTime) / 1000;
            log.info("🎉 === FULL IMAGE COMPRESSION COMPLETED ===");
            log.info("⏱️ Total Duration: {}s ({:.1f} minutes)", duration, duration / 60.0);
            log.info("📁 Compressed Location: {}", RESOURCES_BASE);
            log.info("🗑️ Original images deleted");
            
        } catch (Exception e) {
            log.error("💥 Migration failed: {}", e.getMessage(), e);
        }
    }

    public void archivePlaces() {
        log.info("=== 📸 Starting Place Images Compression ===");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        List<Place> places = placeRepository.findAll();
        log.info("Found {} places to process", places.size());
        
        // Process sequentially to avoid overwhelming server
        for (Place place : places) {
            try {
                boolean updated = false;
                String resourcePath = RESOURCES_BASE + "places/" + place.getPlaceId() + "/";
                
                // Skip if already in resources folder
                if (place.getThumbnail() != null && place.getThumbnail().contains("/resources/")) {
                    skipped.incrementAndGet();
                    continue;
                }

                if (isMinIOUrl(place.getThumbnail())) {
                    String compressedUrl = imageArchiveService.compressAndDelete(place.getThumbnail(), resourcePath);
                    if (compressedUrl != null && !compressedUrl.equals(place.getThumbnail())) {
                        place.setThumbnail(compressedUrl);
                        updated = true;
                    }
                }
                
                if (place.getImages() != null && !place.getImages().equals("[]")) {
                    String compressedImages = imageArchiveService.compressImagesJsonDirectly(place.getImages(), resourcePath);
                    if (!compressedImages.equals(place.getImages())) {
                        place.setImages(compressedImages);
                        updated = true;
                    }
                }
                
                if (updated) {
                    placeRepository.update(place);
                    success.incrementAndGet();
                    
                    // Log progress every 50 records
                    if (success.get() % 50 == 0) {
                        log.info("📊 Progress: {} processed, {} success, {} failed, {} skipped", 
                                success.get() + failed.get() + skipped.get(), 
                                success.get(), failed.get(), skipped.get());
                    }
                } else {
                    skipped.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed place {}: {}", place.getPlaceId(), e.getMessage());
            }
        }
        
        log.info("✅ Success: {}, ❌ Failed: {}, ⏭️ Skipped: {}", success.get(), failed.get(), skipped.get());
    }

    public void archivePlaceReviews() {
        log.info("=== 📸 Starting Review Images Compression ===");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        List<PlaceReview> reviews = placeReviewRepository.findAll();
        log.info("Found {} reviews", reviews.size());
        
        for (PlaceReview review : reviews) {
            try {
                boolean updated = false;
                String resourcePath = RESOURCES_BASE + "reviews/" + review.getId() + "/";
                
                if (isMinIOUrl(review.getProfilePicture())) {
                    String compressedUrl = imageArchiveService.compressAndDelete(review.getProfilePicture(), resourcePath);
                    if (compressedUrl != null && !compressedUrl.equals(review.getProfilePicture())) {
                        review.setProfilePicture(compressedUrl);
                        updated = true;
                    }
                }
                
                if (review.getImages() != null && !review.getImages().equals("[]")) {
                    String compressedImages = compressReviewImagesArray(review.getImages(), resourcePath);
                    if (!compressedImages.equals(review.getImages())) {
                        review.setImages(compressedImages);
                        updated = true;
                    }
                }
                
                if (updated) {
                    placeReviewRepository.update(review);
                    success.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed review {}: {}", review.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Success: {}, ❌ Failed: {}, ⏭️ Skipped: {}", success.get(), failed.get(), skipped.get());
    }

    public void archiveActivities() {
        log.info("=== 📸 Starting Activity Images Compression ===");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        List<Activity> activities = activityRepository.findAll();
        log.info("Found {} activities", activities.size());
        
        for (Activity activity : activities) {
            try {
                if (isMinIOUrl(activity.getPhotoUrl())) {
                    String resourcePath = RESOURCES_BASE + "activities/" + activity.getId() + "/";
                    String compressedUrl = imageArchiveService.compressAndDelete(activity.getPhotoUrl(), resourcePath);
                    
                    if (compressedUrl != null && !compressedUrl.equals(activity.getPhotoUrl())) {
                        activity.setPhotoUrl(compressedUrl);
                        activityRepository.update(activity);
                        success.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                } else {
                    skipped.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed activity {}: {}", activity.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Success: {}, ❌ Failed: {}, ⏭️ Skipped: {}", success.get(), failed.get(), skipped.get());
    }

    public void archiveActivityBookings() {
        log.info("=== 📸 Starting Booking Images Compression ===");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        List<ActivityBooking> bookings = activityBookingRepository.findAll();
        log.info("Found {} bookings", bookings.size());
        
        for (ActivityBooking booking : bookings) {
            try {
                boolean updated = false;
                String resourcePath = RESOURCES_BASE + "bookings/" + booking.getId() + "/";
                
                if (isMinIOUrl(booking.getThumbnail())) {
                    String compressedUrl = imageArchiveService.compressAndDelete(booking.getThumbnail(), resourcePath);
                    if (compressedUrl != null && !compressedUrl.equals(booking.getThumbnail())) {
                        booking.setThumbnail(compressedUrl);
                        updated = true;
                    }
                }
                
                if (booking.getImages() != null && !booking.getImages().equals("[]")) {
                    String compressedImages = imageArchiveService.compressImagesJsonDirectly(booking.getImages(), resourcePath);
                    if (!compressedImages.equals(booking.getImages())) {
                        booking.setImages(compressedImages);
                        updated = true;
                    }
                }
                
                if (booking.getItinerary() != null && !booking.getItinerary().equals("[]")) {
                    String compressedItinerary = compressItineraryImages(booking.getItinerary(), resourcePath + "itinerary/");
                    if (!compressedItinerary.equals(booking.getItinerary())) {
                        booking.setItinerary(compressedItinerary);
                        updated = true;
                    }
                }
                
                if (updated) {
                    activityBookingRepository.update(booking);
                    success.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed booking {}: {}", booking.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Success: {}, ❌ Failed: {}, ⏭️ Skipped: {}", success.get(), failed.get(), skipped.get());
    }

    public void archiveFoods() {
        log.info("=== 📸 Starting Food Images Compression ===");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        List<Food> foods = foodRepository.findAll();
        log.info("Found {} foods", foods.size());
        
        for (Food food : foods) {
            try {
                if (isMinIOUrl(food.getImageUrl())) {
                    String resourcePath = RESOURCES_BASE + "foods/" + food.getId() + "/";
                    String compressedUrl = imageArchiveService.compressAndDelete(food.getImageUrl(), resourcePath);
                    
                    if (compressedUrl != null && !compressedUrl.equals(food.getImageUrl())) {
                        food.setImageUrl(compressedUrl);
                        foodRepository.update(food);
                        success.incrementAndGet();
                    } else {
                        skipped.incrementAndGet();
                    }
                } else {
                    skipped.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed food {}: {}", food.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Success: {}, ❌ Failed: {}, ⏭️ Skipped: {}", success.get(), failed.get(), skipped.get());
    }

    public void archiveExpenses() {
        log.info("=== 📸 Starting Expense Images Compression ===");
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger skipped = new AtomicInteger(0);
        
        List<Expense> expenses = expenseRepository.findAll();
        log.info("Found {} expenses", expenses.size());
        
        for (Expense expense : expenses) {
            try {
                boolean updated = false;
                String resourcePath = RESOURCES_BASE + "expenses/" + expense.getId() + "/";
                
                if (isMinIOUrl(expense.getReceiptUrl())) {
                    String compressedUrl = imageArchiveService.compressAndDelete(expense.getReceiptUrl(), resourcePath);
                    if (compressedUrl != null && !compressedUrl.equals(expense.getReceiptUrl())) {
                        expense.setReceiptUrl(compressedUrl);
                        updated = true;
                    }
                }
                
                if (expense.getPhotoUrls() != null && expense.getPhotoUrls().length > 0) {
                    List<String> urlList = Arrays.asList(expense.getPhotoUrls());
                    List<String> minioUrls = urlList.stream().filter(this::isMinIOUrl).toList();
                    
                    if (!minioUrls.isEmpty()) {
                        Map<String, String> compressedUrls = imageArchiveService.archiveAndCompressBatch(minioUrls, resourcePath);
                        
                        String[] newPhotoUrls = urlList.stream()
                                .map(url -> compressedUrls.getOrDefault(url, url))
                                .toArray(String[]::new);
                        
                        expense.setPhotoUrls(newPhotoUrls);
                        updated = true;
                    }
                }
                
                if (updated) {
                    expenseRepository.update(expense);
                    success.incrementAndGet();
                } else {
                    skipped.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed expense {}: {}", expense.getId(), e.getMessage());
            }
        }
        
        log.info("✅ Success: {}, ❌ Failed: {}, ⏭️ Skipped: {}", success.get(), failed.get(), skipped.get());
    }

    private boolean isMinIOUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        // Skip if already in resources folder
        if (url.contains("/resources/") || url.contains("/resource/")) {
            return false;
        }
        return url.contains("onestudy.id.vn") && !url.startsWith("http://") && !url.contains("google");
    }

    private String compressReviewImagesArray(String imagesJson, String resourcePath) {
        try {
            JsonNode rootNode = objectMapper.readTree(imagesJson);
            if (!rootNode.isArray()) {
                return imagesJson;
            }
            
            ArrayNode arrayNode = (ArrayNode) rootNode;
            List<String> minioUrls = new ArrayList<>();
            
            for (JsonNode node : arrayNode) {
                if (node.isTextual() && isMinIOUrl(node.asText())) {
                    minioUrls.add(node.asText());
                }
            }
            
            if (minioUrls.isEmpty()) {
                return imagesJson;
            }
            
            Map<String, String> compressedUrls = imageArchiveService.archiveAndCompressBatch(minioUrls, resourcePath);
            
            ArrayNode newArray = objectMapper.createArrayNode();
            for (JsonNode node : arrayNode) {
                if (node.isTextual()) {
                    String oldUrl = node.asText();
                    String newUrl = compressedUrls.getOrDefault(oldUrl, oldUrl);
                    newArray.add(newUrl);
                } else {
                    newArray.add(node);
                }
            }
            
            return objectMapper.writeValueAsString(newArray);
            
        } catch (Exception e) {
            log.error("Error compressing review images: {}", e.getMessage());
            return imagesJson;
        }
    }

    private String compressItineraryImages(String itineraryJson, String resourcePath) {
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
                    List<String> minioUrls = new ArrayList<>();
                    
                    for (JsonNode imgNode : imagesArray) {
                        if (imgNode.isTextual() && isMinIOUrl(imgNode.asText())) {
                            minioUrls.add(imgNode.asText());
                        }
                    }
                    
                    if (!minioUrls.isEmpty()) {
                        Map<String, String> compressedUrls = imageArchiveService.archiveAndCompressBatch(minioUrls, resourcePath);
                        
                        ArrayNode newImagesArray = objectMapper.createArrayNode();
                        for (JsonNode imgNode : imagesArray) {
                            if (imgNode.isTextual()) {
                                String oldUrl = imgNode.asText();
                                String newUrl = compressedUrls.getOrDefault(oldUrl, oldUrl);
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
            log.error("Error compressing itinerary images: {}", e.getMessage());
            return itineraryJson;
        }
    }
}
