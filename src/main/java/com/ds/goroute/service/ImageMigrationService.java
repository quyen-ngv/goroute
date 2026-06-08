package com.ds.goroute.service;

import java.util.List;
import java.util.Map;

public interface ImageMigrationService {
    /**
     * Download, compress and upload a single image URL to MinIO
     * @param imageUrl Google image URL
     * @param targetPath Target path in MinIO (e.g., "places/{placeId}/")
     * @return New MinIO URL or null if failed
     */
    String migrateImage(String imageUrl, String targetPath);
    
    /**
     * Migrate multiple images in parallel for performance
     * @param imageUrls List of Google image URLs
     * @param targetPath Target path in MinIO
     * @return Map of original URL to new MinIO URL (failed images excluded)
     */
    Map<String, String> migrateImages(List<String> imageUrls, String targetPath);
    
    /**
     * Migrate images from JSON array string
     * @param imagesJson JSON array of image objects [{"image": "url", "title": "..."}]
     * @param targetPath Target path in MinIO
     * @return Updated JSON with new MinIO URLs
     */
    String migrateImagesJson(String imagesJson, String targetPath);
}
