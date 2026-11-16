package com.ds.goroute.service.imagecompression;

import com.ds.goroute.entity.ImageCompressionRecord;
import com.ds.goroute.repository.ImageCompressionRepository;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.thirdparty.caesium.CaesiumClient;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionOutcome;
import com.ds.goroute.type.ImageCompressionSource;
import com.ds.goroute.type.ImageCompressionTier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Compresses one stored image with caesium and <b>overwrites it under the same key</b>
 * (epic 00 §9b). Storage is nearly full, so nothing is ever copied: URLs stay valid everywhere
 * they were copied to, and the change cannot be undone. The file is written back only when the
 * WebP saves at least {@code minSavingsPercent}. Every outcome a later run should not repeat
 * (COMPRESSED, NO_SAVINGS, NOT_IMAGE) is recorded in {@code image_compressions}.
 *
 * <p>The WebP keeps the source EXIF, GPS included (owner decision 2026-11-10), with the pixels
 * rotated upright; see {@code caesium_service/server.py}.
 */
@Slf4j
@Service
public class StoredImageCompressor {

    /** Larger files are not read at all; the caesium service refuses above 30 MB too. */
    public static final long MAX_INPUT_BYTES = 30L * 1024 * 1024;
    private static final String WEBP = "image/webp";
    private static final int MAX_MESSAGE_LENGTH = 300;

    private final StorageService storageService;
    private final CaesiumClient caesiumClient;
    private final ImageCompressionRepository repository;

    public StoredImageCompressor(StorageService storageService,
                                 CaesiumClient caesiumClient,
                                 ImageCompressionRepository repository) {
        this.storageService = storageService;
        this.caesiumClient = caesiumClient;
        this.repository = repository;
    }

    public record Request(String key,
                          ImageCompressionGroup group,
                          ImageCompressionTier tier,
                          int quality,
                          int maxLongEdge,
                          int minSavingsPercent,
                          ImageCompressionSource source,
                          boolean dryRun) {
    }

    public record Result(String key,
                         ImageCompressionOutcome outcome,
                         Long originalBytes,
                         Long compressedBytes,
                         String message) {
    }

    /** Whether a key names an image file by extension; anything else (videos, audio) is never read. */
    public static boolean hasImageExtension(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp");
    }

    /** Reads the object, compresses it and writes it back. */
    public Result compressStored(Request request) {
        byte[] original;
        try {
            original = storageService.readObject(request.key());
        } catch (Exception e) {
            return failed(request.key(), null, e);
        }
        if (original == null) {
            return new Result(request.key(), ImageCompressionOutcome.MISSING, null, null, null);
        }
        return compressBytes(request, original);
    }

    /** As {@link #compressStored} for bytes already in memory, such as a file that was just uploaded. */
    public Result compressBytes(Request request, byte[] original) {
        long originalBytes = original.length;
        if (originalBytes > MAX_INPUT_BYTES) {
            return new Result(request.key(), ImageCompressionOutcome.FAILED, originalBytes, null,
                    "Ảnh lớn hơn " + (MAX_INPUT_BYTES / 1024 / 1024) + " MB");
        }
        if (!isStillImage(original)) {
            return record(request, ImageCompressionOutcome.NOT_IMAGE, originalBytes, originalBytes);
        }
        byte[] webp;
        try {
            webp = caesiumClient.compressToWebp(original, request.quality(), request.maxLongEdge());
        } catch (Exception e) {
            return failed(request.key(), originalBytes, e);
        }
        long maxAllowed = originalBytes * (100 - request.minSavingsPercent()) / 100;
        if (webp.length > maxAllowed) {
            return record(request, ImageCompressionOutcome.NO_SAVINGS, originalBytes, webp.length);
        }
        if (request.dryRun()) {
            return new Result(request.key(), ImageCompressionOutcome.WOULD_COMPRESS, originalBytes, (long) webp.length, null);
        }
        try {
            storageService.uploadBytes(webp, request.key(), WEBP);
        } catch (Exception e) {
            return failed(request.key(), originalBytes, e);
        }
        return record(request, ImageCompressionOutcome.COMPRESSED, originalBytes, webp.length);
    }

    private Result record(Request request, ImageCompressionOutcome outcome, long originalBytes, long compressedBytes) {
        if (!request.dryRun()) {
            try {
                repository.save(ImageCompressionRecord.builder()
                        .objectKey(request.key())
                        .groupCode(request.group().name())
                        .source(request.source().name())
                        .outcome(outcome.name())
                        .tier(request.tier().name())
                        .quality(request.quality())
                        .maxLongEdge(request.maxLongEdge())
                        .originalBytes(originalBytes)
                        .compressedBytes(outcome == ImageCompressionOutcome.COMPRESSED ? compressedBytes : originalBytes)
                        .build());
            } catch (Exception e) {
                // The file is already right; only a later run will look at it again.
                log.warn("Image compression: could not record {}: {}", request.key(), e.getMessage());
            }
        }
        return new Result(request.key(), outcome, originalBytes, compressedBytes, null);
    }

    private Result failed(String key, Long originalBytes, Exception e) {
        log.warn("Image compression failed for {}: {}", key, e.getMessage());
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return new Result(key, ImageCompressionOutcome.FAILED, originalBytes, null,
                message.length() > MAX_MESSAGE_LENGTH ? message.substring(0, MAX_MESSAGE_LENGTH) : message);
    }

    /**
     * A JPEG, PNG or still WebP by its first bytes. An animated WebP is refused: caesium would
     * keep only its first frame.
     */
    static boolean isStillImage(byte[] bytes) {
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return true;
        }
        if (bytes.length >= 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return true;
        }
        if (bytes.length >= 21 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            boolean extended = bytes[12] == 'V' && bytes[13] == 'P' && bytes[14] == '8' && bytes[15] == 'X';
            boolean animated = extended && (bytes[20] & 0x02) != 0;
            return !animated;
        }
        return false;
    }
}
