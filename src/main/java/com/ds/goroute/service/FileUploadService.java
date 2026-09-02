package com.ds.goroute.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * The single door for every image and video entering the product (MOD-05).
 *
 * <p>Nothing else calls the storage layer directly with user-supplied bytes. Before this
 * was true, seven separate paths reached storage with their own hand-written checks or
 * none at all -- avatars, partner media, administrator uploads, fetch-from-URL and
 * location imagery -- which meant an image check could only ever protect a fraction of
 * the product while looking like it protected all of it.
 */
public interface FileUploadService {

    /** Ordinary user upload; throws when the image is rejected. */
    String uploadImage(UUID userId, MultipartFile file);

    /**
     * Batch upload where each image succeeds or fails on its own.
     * A rejected image never fails the rest of the batch.
     */
    List<ImageUploadOutcome> uploadImages(ImageUploadRequest request, List<MultipartFile> files);

    /** Single upload through a named entry point; each image reports its own outcome. */
    ImageUploadOutcome uploadImage(ImageUploadRequest request, MultipartFile file);

    /**
     * Fetches an image from an external address and puts it through exactly the same
     * checks as an uploaded one. An image the product downloaded is still an image the
     * product publishes.
     */
    ImageUploadOutcome uploadImageFromUrl(ImageUploadRequest request, String sourceUrl);

    String uploadVideo(UUID userId, MultipartFile file);

    /** Operator upload through a fixed storage prefix, for curated (non-user) video assets. */
    String uploadVideo(String objectPrefix, MultipartFile file);
}
