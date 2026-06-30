package com.ds.goroute.controller;

import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.job.ImageMigrationJob;
import com.ds.goroute.job.ReviewCleanupJob;
import com.ds.goroute.service.BaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/images")
@RequiredArgsConstructor
@Slf4j
public class ImageMigrationController extends BaseService {

    private final ImageMigrationJob imageMigrationJob;
    private final ReviewCleanupJob reviewCleanupJob;

    /**
     * Trigger full image migration (async)
     * GET /v1/api/admin/images/migrate/all
     */
    @PostMapping("/migrate/all")
    public ResponseEntity<BaseResponse<String>> migrateAllImages() {
        log.info("Triggering full image migration");
        
        try {
            imageMigrationJob.runFullMigration();
            return ResponseEntity.ok(ofSucceeded("Migration started. Check logs for progress."));
        } catch (Exception e) {
            log.error("Failed to start migration: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, "Failed to start migration: " + e.getMessage()));
        }
    }

    /**
     * Migrate places only
     */
    @PostMapping("/migrate/places")
    public ResponseEntity<BaseResponse<String>> migratePlaces() {
        log.info("Triggering place image migration");
        
        try {
            imageMigrationJob.migratePlaces();
            return ResponseEntity.ok(ofSucceeded("Place migration completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, e.getMessage()));
        }
    }

    /**
     * Migrate reviews only
     */
    @PostMapping("/migrate/reviews")
    public ResponseEntity<BaseResponse<String>> migrateReviews() {
        log.info("Triggering review image migration");
        
        try {
            imageMigrationJob.migratePlaceReviews();
            return ResponseEntity.ok(ofSucceeded("Review migration completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, e.getMessage()));
        }
    }

    /**
     * Migrate activities only
     */
    @PostMapping("/migrate/activities")
    public ResponseEntity<BaseResponse<String>> migrateActivities() {
        log.info("Triggering activity image migration");
        
        try {
            imageMigrationJob.migrateActivities();
            return ResponseEntity.ok(ofSucceeded("Activity migration completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, e.getMessage()));
        }
    }

    /**
     * Migrate bookings only
     */
    @PostMapping("/migrate/bookings")
    public ResponseEntity<BaseResponse<String>> migrateBookings() {
        log.info("Triggering booking image migration");
        
        try {
            imageMigrationJob.migrateActivityBookings();
            return ResponseEntity.ok(ofSucceeded("Booking migration completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, e.getMessage()));
        }
    }

    /**
     * Migrate foods only
     */
    @PostMapping("/migrate/foods")
    public ResponseEntity<BaseResponse<String>> migrateFoods() {
        log.info("Triggering food image migration");
        
        try {
            imageMigrationJob.migrateFoods();
            return ResponseEntity.ok(ofSucceeded("Food migration completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, e.getMessage()));
        }
    }

    /**
     * Migrate expenses only
     */
    @PostMapping("/migrate/expenses")
    public ResponseEntity<BaseResponse<String>> migrateExpenses() {
        log.info("Triggering expense image migration");
        
        try {
            imageMigrationJob.migrateExpenses();
            return ResponseEntity.ok(ofSucceeded("Expense migration completed"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, e.getMessage()));
        }
    }

    // ==================== REVIEW CLEANUP ====================

    /**
     * Cleanup low-quality reviews - keep only top 200 reviews per place
     */
    @PostMapping("/reviews/cleanup")
    public ResponseEntity<BaseResponse<String>> cleanupReviews() {
        log.info("Triggering review cleanup job");
        
        try {
            reviewCleanupJob.cleanupAllPlaces();
            return ResponseEntity.ok(ofSucceeded("Review cleanup started. Check logs for progress."));
        } catch (Exception e) {
            log.error("Failed to start review cleanup: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, "Failed to start review cleanup: " + e.getMessage()));
        }
    }

    /**
     * Cleanup reviews for a specific place
     */
    @PostMapping("/reviews/cleanup/{placeId}")
    public ResponseEntity<BaseResponse<?>> cleanupPlaceReviews(@PathVariable UUID placeId) {
        log.info("Triggering review cleanup for place: {}", placeId);
        
        try {
            Map<String, Integer> result = reviewCleanupJob.cleanupPlaceReviews(placeId);
            return ResponseEntity.ok(ofSucceeded(result));
        } catch (Exception e) {
            log.error("Failed to cleanup reviews for place {}: {}", placeId, e.getMessage());
            return ResponseEntity.internalServerError()
                    .body(ofFailed(500, "Failed to cleanup reviews: " + e.getMessage()));
        }
    }
}
