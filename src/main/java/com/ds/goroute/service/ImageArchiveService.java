package com.ds.goroute.service;

import java.util.List;
import java.util.Map;

public interface ImageArchiveService {
    
    /**
     * Archive and compress a single image with 70% size reduction
     * @param imageUrl Current MinIO image URL
     * @param targetPath New compressed image path (e.g., "places/{placeId}/compressed/")
     * @return New compressed URL or null if failed
     */
    String archiveAndCompress(String imageUrl, String targetPath);
    
    /**
     * Archive and compress multiple images in parallel
     * @param imageUrls List of current MinIO URLs
     * @param targetPath Target path for compressed images
     * @return Map of original URL to new compressed URL
     */
    Map<String, String> archiveAndCompressBatch(List<String> imageUrls, String targetPath);
    
    /**
     * Move original image to archive folder
     * @param imageUrl Original MinIO URL
     * @param archivePath Archive path (e.g., "archive/places/{placeId}/")
     * @return Archive URL or null if failed
     */
    String moveToArchive(String imageUrl, String archivePath);
    
    /**
     * Delete original image after successful archive
     * @param imageUrl Original MinIO URL to delete
     * @return true if deleted successfully
     */
    boolean deleteOriginal(String imageUrl);
    
    /**
     * Archive images from JSON array string and compress
     * @param imagesJson JSON array of image objects
     * @param targetPath Target path for compressed images
     * @param archivePath Archive path for originals
     * @return Updated JSON with new compressed URLs
     */
    String archiveAndCompressImagesJson(String imagesJson, String targetPath, String archivePath);
    
    /**
     * Compress and delete original image directly (no backup)
     * @param imageUrl Current MinIO image URL
     * @param targetPath New compressed image path
     * @return New compressed URL or null if failed
     */
    String compressAndDelete(String imageUrl, String targetPath);
    
    /**
     * Compress images from JSON directly without backup
     * @param imagesJson JSON array of image objects  
     * @param targetPath Target path for compressed images
     * @return Updated JSON with new compressed URLs
     */
    String compressImagesJsonDirectly(String imagesJson, String targetPath);
}