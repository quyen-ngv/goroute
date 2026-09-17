package com.ds.goroute.job;

import com.ds.goroute.entity.*;
import com.ds.goroute.repository.*;
import com.ds.goroute.service.ImageMigrationService;
import com.ds.goroute.service.StorageService;
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
    private final StorageService storageService;
    private final PlaceRepository placeRepository;
    private final PlaceReviewRepository placeReviewRepository;
    private final ActivityRepository activityRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final FoodRepository foodRepository;
    private final MediaAssetRepository mediaAssetRepository;
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
        
        // `places` is a snapshot of the whole table taken before any image was downloaded, and a
        // full run takes hours. Writing a row back from that snapshot would restore every column
        // to the value it had when the run started, silently undoing anything edited meanwhile.
        // So each row is re-read immediately before it is written and only the image columns this
        // job owns are carried over -- and only while they still hold the value the migration
        // started from.
        for (Place place : places) {
            try {
                String targetPath = "places/" + place.getPlaceId() + "/";

                String newThumbnail = isExternalUrl(place.getThumbnail())
                        ? imageMigrationService.migrateImage(place.getThumbnail(), targetPath)
                        : null;
                String newImages = place.getImages() != null && !place.getImages().equals("[]")
                        ? imageMigrationService.migrateImagesJson(place.getImages(), targetPath)
                        : null;
                // Migrate menu photos nested inside the menu JSON object.
                String newMenu = place.getMenu() != null && !place.getMenu().trim().isEmpty()
                        ? imageMigrationService.migrateMenuJson(place.getMenu(), targetPath)
                        : null;

                boolean thumbnailChanged = newThumbnail != null && !newThumbnail.equals(place.getThumbnail());
                boolean imagesChanged = newImages != null && !newImages.equals(place.getImages());
                boolean menuChanged = newMenu != null && !newMenu.equals(place.getMenu());
                if (!thumbnailChanged && !imagesChanged && !menuChanged) {
                    continue;
                }

                Place current = placeRepository.findById(place.getId()).orElse(null);
                if (current == null) {
                    log.debug("Place {} disappeared during migration", place.getPlaceId());
                    continue;
                }

                boolean updated = false;
                if (thumbnailChanged && Objects.equals(current.getThumbnail(), place.getThumbnail())) {
                    current.setThumbnail(newThumbnail);
                    updated = true;
                }
                if (imagesChanged && Objects.equals(current.getImages(), place.getImages())) {
                    current.setImages(newImages);
                    updated = true;
                }
                if (menuChanged && Objects.equals(current.getMenu(), place.getMenu())) {
                    current.setMenu(newMenu);
                    updated = true;
                }
                if (!updated) {
                    log.info("Place {} was edited during the migration; leaving it alone", place.getPlaceId());
                    continue;
                }

                placeRepository.update(current);
                success.incrementAndGet();
                log.debug("Migrated place: {}", place.getPlaceId());

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate place {}: {}", place.getPlaceId(), e.getMessage(), e);
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
        
        // Re-read before writing where the row can be found again, for the same reason as
        // migratePlaces above. PlaceReviewRepository exposes no read by primary key, so a review
        // with no scraped review id still has to be written from the snapshot.
        for (PlaceReview review : reviews) {
            try {
                String targetPath = "reviews/" + review.getId() + "/";

                boolean profileChanged = false;
                String newProfilePicture = null;
                if (review.getProfilePicture() != null && !isManagedImage(review.getProfilePicture())) {
                    String migrated = imageMigrationService.migrateCompressedImage(
                            review.getProfilePicture(), targetPath + "profile/");
                    newProfilePicture = isManagedImage(migrated) ? migrated : null;
                    profileChanged = true;
                }

                // Migrate images JSON array
                String newImages = review.getImages() != null && !review.getImages().equals("[]")
                        ? migrateReviewImagesArray(review.getImages(), targetPath)
                        : null;
                boolean imagesChanged = newImages != null && !newImages.equals(review.getImages());

                if (!profileChanged && !imagesChanged) {
                    continue;
                }

                PlaceReview current = rereadReview(review);
                if (current == null) {
                    current = review;
                }
                boolean updated = false;
                if (profileChanged && Objects.equals(current.getProfilePicture(), review.getProfilePicture())) {
                    current.setProfilePicture(newProfilePicture);
                    updated = true;
                }
                if (imagesChanged && Objects.equals(current.getImages(), review.getImages())) {
                    current.setImages(newImages);
                    updated = true;
                }
                if (!updated) {
                    log.info("Review {} was edited during the migration; leaving it alone", review.getId());
                    continue;
                }

                placeReviewRepository.update(current);
                success.incrementAndGet();

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate review {}: {}", review.getId(), e.getMessage(), e);
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
        
        // Re-read before writing, for the same reason as migratePlaces above.
        for (Activity activity : activities) {
            try {
                if (isExternalUrl(activity.getPhotoUrl())) {
                    String targetPath = "activities/" + activity.getId() + "/";
                    String newUrl = imageMigrationService.migrateImage(activity.getPhotoUrl(), targetPath);

                    if (newUrl != null && !newUrl.equals(activity.getPhotoUrl())) {
                        Activity current = activityRepository.findById(activity.getId()).orElse(null);
                        if (current == null || !Objects.equals(current.getPhotoUrl(), activity.getPhotoUrl())) {
                            log.info("Activity {} changed during the migration; leaving it alone", activity.getId());
                            continue;
                        }
                        current.setPhotoUrl(newUrl);
                        activityRepository.update(current);
                        success.incrementAndGet();
                    }
                }

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate activity {}: {}", activity.getId(), e.getMessage(), e);
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
        
        // Re-read before writing, for the same reason as migratePlaces above.
        for (ActivityBooking booking : bookings) {
            try {
                String targetPath = "bookings/" + booking.getId() + "/";

                String newThumbnail = isExternalUrl(booking.getThumbnail())
                        ? imageMigrationService.migrateImage(booking.getThumbnail(), targetPath)
                        : null;
                String newImages = booking.getImages() != null && !booking.getImages().equals("[]")
                        ? imageMigrationService.migrateImagesJson(booking.getImages(), targetPath)
                        : null;
                // Migrate itinerary JSON (complex structure)
                String newItinerary = booking.getItinerary() != null && !booking.getItinerary().equals("[]")
                        ? migrateItineraryImages(booking.getItinerary(), targetPath + "itinerary/")
                        : null;

                boolean thumbnailChanged = newThumbnail != null && !newThumbnail.equals(booking.getThumbnail());
                boolean imagesChanged = newImages != null && !newImages.equals(booking.getImages());
                boolean itineraryChanged = newItinerary != null && !newItinerary.equals(booking.getItinerary());
                if (!thumbnailChanged && !imagesChanged && !itineraryChanged) {
                    continue;
                }

                ActivityBooking current = activityBookingRepository.findById(booking.getId()).orElse(null);
                if (current == null) {
                    log.debug("Booking {} disappeared during migration", booking.getId());
                    continue;
                }

                boolean updated = false;
                if (thumbnailChanged && Objects.equals(current.getThumbnail(), booking.getThumbnail())) {
                    current.setThumbnail(newThumbnail);
                    updated = true;
                }
                if (imagesChanged && Objects.equals(current.getImages(), booking.getImages())) {
                    current.setImages(newImages);
                    updated = true;
                }
                if (itineraryChanged && Objects.equals(current.getItinerary(), booking.getItinerary())) {
                    current.setItinerary(newItinerary);
                    updated = true;
                }
                if (!updated) {
                    log.info("Booking {} was edited during the migration; leaving it alone", booking.getId());
                    continue;
                }

                activityBookingRepository.update(current);
                success.incrementAndGet();

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate booking {}: {}", booking.getId(), e.getMessage(), e);
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
        
        // Re-read before writing, for the same reason as migratePlaces above.
        for (Food food : foods) {
            try {
                if (isExternalUrl(food.getImageUrl())) {
                    String targetPath = "foods/" + food.getId() + "/";
                    String newUrl = imageMigrationService.migrateImage(food.getImageUrl(), targetPath);

                    if (newUrl != null && !newUrl.equals(food.getImageUrl())) {
                        Food current = foodRepository.findFoodById(food.getId()).orElse(null);
                        if (current == null || !Objects.equals(current.getImageUrl(), food.getImageUrl())) {
                            log.info("Food {} changed during the migration; leaving it alone", food.getId());
                            continue;
                        }
                        current.setImageUrl(newUrl);
                        foodRepository.update(current);
                        success.incrementAndGet();
                    }
                }

            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate food {}: {}", food.getId(), e.getMessage(), e);
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
        
        List<MediaAsset> assets = mediaAssetRepository.findByEntityType("EXPENSE");
        log.info("Found {} expense media assets to process", assets.size());
        
        for (MediaAsset asset : assets) {
            try {
                String targetPath = "expenses/" + asset.getEntityId() + "/";
                if (isExternalUrl(asset.getUrl())) {
                    String newUrl = imageMigrationService.migrateImage(asset.getUrl(), targetPath);
                    if (newUrl != null && !newUrl.equals(asset.getUrl())) {
                        mediaAssetRepository.updateUrl(asset.getId(), newUrl);
                    }
                    success.incrementAndGet();
                }
                
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("Failed to migrate expense media asset {}: {}", asset.getId(), e.getMessage(), e);
            }
        }
        
        log.info("Expense migration: {} success, {} failed", success.get(), failed.get());
    }

    // Helper methods

    /**
     * Returns the review as it stands now, or null when it cannot be looked up again.
     *
     * <p>The only read this repository offers besides the full scan is by scraped review id, so
     * a Goroute-authored review has no key to re-read by and keeps the old snapshot write.
     */
    private PlaceReview rereadReview(PlaceReview snapshot) {
        if (snapshot.getReviewId() == null || snapshot.getReviewId().isBlank()) {
            return null;
        }
        PlaceReview current = placeReviewRepository.findByReviewId(snapshot.getReviewId()).orElse(null);
        return current != null && Objects.equals(current.getId(), snapshot.getId()) ? current : null;
    }

    private boolean isExternalUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        // Migrate all HTTP URLs except our own MinIO domain
        return url.startsWith("http") && !url.contains("onestudy.id.vn");
    }

    private boolean isManagedImage(String url) {
        return storageService.extractObjectKey(url) != null;
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
            List<String> externalUrls = new ArrayList<>();
            for (JsonNode node : arrayNode) {
                if (node.isTextual() && !isManagedImage(node.asText())) {
                    externalUrls.add(node.asText());
                }
            }
            Map<String, String> migratedUrls = externalUrls.isEmpty()
                    ? Map.of()
                    : imageMigrationService.migrateCompressedImages(externalUrls, targetPath);
            ArrayNode newArray = objectMapper.createArrayNode();
            for (JsonNode node : arrayNode) {
                if (node.isTextual()) {
                    String oldUrl = node.asText();
                    String newUrl = isManagedImage(oldUrl) ? oldUrl : migratedUrls.get(oldUrl);
                    if (isManagedImage(newUrl)) {
                        newArray.add(newUrl);
                    }
                }
            }
            
            return objectMapper.writeValueAsString(newArray);
            
        } catch (Exception e) {
            log.error("Error migrating review images array: {}", e.getMessage(), e);
            return "[]";
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
            log.error("Error migrating itinerary images: {}", e.getMessage(), e);
            return itineraryJson;
        }
    }
}
