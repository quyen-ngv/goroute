package com.ds.goroute.service.imagecompression;

import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionSource;
import com.ds.goroute.type.ImageCompressionTier;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * The upload hook (epic 00 §9b): when {@code IMAGE_COMPRESSION.AUTO_ENABLED} is on, an image
 * that was just stored is compressed in the background and overwritten under the same key.
 * The upload itself never waits: the URL is already returned, it simply serves the smaller
 * file a few seconds later. One worker, a short queue; an image that does not fit is left for
 * the console's bulk run, so a burst of uploads cannot pile photos up in the heap.
 */
@Slf4j
@Component
public class AutoImageCompressor {

    /** Each queued task holds the uploaded bytes (up to the 10/50 MB upload limit). */
    private static final int QUEUE_CAPACITY = 16;

    /** Entry points whose uploader is the image's owner, so the owner's plan picks the tier. */
    private static final Set<String> USER_ENTRY_POINTS = Set.of(
            ImageUploadRequest.ImageEntryPoint.USER_UPLOAD,
            ImageUploadRequest.ImageEntryPoint.USER_AVATAR,
            ImageUploadRequest.ImageEntryPoint.CHAT_ATTACHMENT,
            ImageUploadRequest.ImageEntryPoint.CHECKIN_PHOTO,
            ImageUploadRequest.ImageEntryPoint.POST_MEDIA);

    private final StoredImageCompressor compressor;
    private final ImageCompressionSettingsService settingsService;
    private final ImageOwnerTierResolver tierResolver;
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(QUEUE_CAPACITY), runnable -> {
                Thread thread = new Thread(runnable, "image-auto-compress");
                thread.setDaemon(true);
                return thread;
            });

    public AutoImageCompressor(StoredImageCompressor compressor,
                               ImageCompressionSettingsService settingsService,
                               ImageOwnerTierResolver tierResolver) {
        this.compressor = compressor;
        this.settingsService = settingsService;
        this.tierResolver = tierResolver;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Called by the upload service after the file is stored; never throws. */
    public void submit(ImageUploadRequest request, String objectKey, byte[] bytes) {
        try {
            if (ImageUploadRequest.ImageEntryPoint.PARTNER_VERIFICATION.equals(request.entryPoint())) {
                return;
            }
            Optional<ImageCompressionGroup> group = ImageCompressionGroup.forKey(objectKey);
            if (group.isEmpty()) {
                return;
            }
            ImageCompressionSettingsService.Settings settings = settingsService.current();
            if (!settings.autoEnabled()) {
                return;
            }
            executor.execute(() -> compress(request, objectKey, bytes, group.get(), settings));
        } catch (RejectedExecutionException full) {
            log.info("Image auto-compression queue full; {} is left for the bulk run", objectKey);
        } catch (Exception e) {
            log.warn("Image auto-compression not scheduled for {}: {}", objectKey, e.getMessage());
        }
    }

    private void compress(ImageUploadRequest request, String objectKey, byte[] bytes,
                          ImageCompressionGroup group, ImageCompressionSettingsService.Settings settings) {
        ImageCompressionTier tier = USER_ENTRY_POINTS.contains(request.entryPoint())
                ? tierResolver.forUser(request.userId())
                : ImageCompressionTier.PRO;
        ImageCompressionSettingsService.TierSettings tierSettings = settings.forTier(tier);
        StoredImageCompressor.Result result = compressor.compressBytes(new StoredImageCompressor.Request(
                objectKey, group, tier, tierSettings.quality(), tierSettings.maxLongEdge(),
                settings.minSavingsPercent(), ImageCompressionSource.AUTO, false), bytes);
        log.debug("Image auto-compression {}: {} {} -> {}", objectKey, result.outcome(),
                result.originalBytes(), result.compressedBytes());
    }
}
