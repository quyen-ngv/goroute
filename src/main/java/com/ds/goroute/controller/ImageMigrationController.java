package com.ds.goroute.controller;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.job.ImageMigrationJob;
import com.ds.goroute.job.ReviewCleanupJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/api/admin/images")
@RequiredArgsConstructor
@Slf4j
public class ImageMigrationController extends BaseController {

    private static final String INTERNAL_ERROR_OPERATION = "image maintenance";

    private final ImageMigrationJob imageMigrationJob;
    private final ReviewCleanupJob reviewCleanupJob;

    /**
     * Trigger full image migration (async)
     * GET /v1/api/admin/images/migrate/all
     */
    @PostMapping("/migrate/all")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migrateAllImages() {
        log.info("Triggering full image migration");
        
        try {
            imageMigrationJob.runFullMigration();
            return ResponseEntity.ok(ofSucceeded("Migration started. Check logs for progress."));
        } catch (Exception e) {
            return maintenanceFailure("start full image migration", e);
        }
    }

    /**
     * Migrate places only
     */
    @PostMapping("/migrate/places")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migratePlaces() {
        log.info("Triggering place image migration");
        
        try {
            imageMigrationJob.migratePlaces();
            return ResponseEntity.ok(ofSucceeded("Place migration completed"));
        } catch (Exception e) {
            return maintenanceFailure("migrate place images", e);
        }
    }

    /**
     * Migrate reviews only
     */
    @PostMapping("/migrate/reviews")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migrateReviews() {
        log.info("Triggering review image migration");
        
        try {
            imageMigrationJob.migratePlaceReviews();
            return ResponseEntity.ok(ofSucceeded("Review migration completed"));
        } catch (Exception e) {
            return maintenanceFailure("migrate review images", e);
        }
    }

    /**
     * Migrate activities only
     */
    @PostMapping("/migrate/activities")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migrateActivities() {
        log.info("Triggering activity image migration");
        
        try {
            imageMigrationJob.migrateActivities();
            return ResponseEntity.ok(ofSucceeded("Activity migration completed"));
        } catch (Exception e) {
            return maintenanceFailure("migrate activity images", e);
        }
    }

    /**
     * Migrate bookings only
     */
    @PostMapping("/migrate/bookings")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migrateBookings() {
        log.info("Triggering booking image migration");
        
        try {
            imageMigrationJob.migrateActivityBookings();
            return ResponseEntity.ok(ofSucceeded("Booking migration completed"));
        } catch (Exception e) {
            return maintenanceFailure("migrate booking images", e);
        }
    }

    /**
     * Migrate foods only
     */
    @PostMapping("/migrate/foods")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migrateFoods() {
        log.info("Triggering food image migration");
        
        try {
            imageMigrationJob.migrateFoods();
            return ResponseEntity.ok(ofSucceeded("Food migration completed"));
        } catch (Exception e) {
            return maintenanceFailure("migrate food images", e);
        }
    }

    /**
     * Migrate expenses only
     */
    @PostMapping("/migrate/expenses")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> migrateExpenses() {
        log.info("Triggering expense image migration");
        
        try {
            imageMigrationJob.migrateExpenses();
            return ResponseEntity.ok(ofSucceeded("Expense migration completed"));
        } catch (Exception e) {
            return maintenanceFailure("migrate expense images", e);
        }
    }

    // ==================== REVIEW CLEANUP ====================

    /**
     * Cleanup low-quality reviews - keep only top 200 reviews per place
     */
    @PostMapping("/reviews/cleanup")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<String>> cleanupReviews() {
        log.info("Triggering review cleanup job");
        
        try {
            reviewCleanupJob.cleanupAllPlaces();
            return ResponseEntity.ok(ofSucceeded("Review cleanup started. Check logs for progress."));
        } catch (Exception e) {
            return maintenanceFailure("start review cleanup", e);
        }
    }

    /**
     * Cleanup reviews for a specific place
     */
    @PostMapping("/reviews/cleanup/{placeId}")
    @PreAuthorize("@adminAuthorization.can(authentication,'system-maintenance','update')")
    public ResponseEntity<BaseResponse<Map<String, Integer>>> cleanupPlaceReviews(@PathVariable UUID placeId) {
        log.info("Triggering review cleanup for place: {}", placeId);
        
        try {
            Map<String, Integer> result = reviewCleanupJob.cleanupPlaceReviews(placeId);
            return ResponseEntity.ok(ofSucceeded(result));
        } catch (Exception e) {
            return maintenanceFailure("clean up reviews for a place", e);
        }
    }

    private <T> ResponseEntity<BaseResponse<T>> maintenanceFailure(String operation, Exception exception) {
        log.error("Failed to perform {} {}", INTERNAL_ERROR_OPERATION, operation, exception);
        return ResponseEntity.internalServerError().body(
                BaseResponse.<T>ofFailed(
                        getRequestId(),
                        getBusinessError(ErrorConstant.INTERNAL_SERVER_ERROR),
                        null));
    }
}
