package com.ds.goroute.service;

import com.ds.goroute.config.CaesiumProperties;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CompressPlaceImagesRequest;
import com.ds.goroute.dto.response.PlaceImageCompressionResponse;
import com.ds.goroute.entity.PlaceImageState;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ImageCompressionRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.imagecompression.StoredImageCompressor;
import com.ds.goroute.thirdparty.caesium.CaesiumClient;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionSource;
import com.ds.goroute.type.ImageCompressionTier;
import com.ds.goroute.type.PlaceImageCompressionOutcome;
import com.ds.goroute.type.PlaceImageCompressionStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Compresses a place's thumbnail and images with caesium (epic 06 §2.13).
 *
 * <p>Storage is nearly full, so nothing is ever copied: each WebP <b>overwrites the file under
 * its own key</b>. URLs never change, so {@code places.thumbnail/images} and every other table
 * that copied a place image URL (activities, saved spots…) are left as they are and get the
 * smaller file too. The cost is that compression cannot be undone, which is why the console
 * offers a dry run and a single-image preview first. Only a file that was read from our own
 * bucket is written back, and only when the WebP saves at least {@code minSavingsPercent}.
 *
 * <p>A place is recorded in {@code places.image_compression} only when no image FAILED; an
 * incomplete place stays pending and the next run retries it. Its files already overwritten
 * then come back as NO_SAVINGS.
 */
@Slf4j
@Service
public class PlaceImageCompressionService {

    static final int DEFAULT_MIN_SAVINGS_PERCENT = 10;
    /** Matches the caesium service's slots (MAX_CONCURRENT); more would only queue there. */
    private static final int PARALLEL_IMAGES = 2;

    private final PlaceRepository placeRepository;
    private final StorageService storageService;
    private final CaesiumClient caesiumClient;
    private final CaesiumProperties caesiumProperties;
    private final ObjectMapper objectMapper;
    private final StoredImageCompressor storedImageCompressor;
    private final ImageCompressionRepository imageCompressionRepository;
    private final ExecutorService imageExecutor;

    public PlaceImageCompressionService(PlaceRepository placeRepository,
                                        StorageService storageService,
                                        CaesiumClient caesiumClient,
                                        CaesiumProperties caesiumProperties,
                                        ObjectMapper objectMapper,
                                        StoredImageCompressor storedImageCompressor,
                                        ImageCompressionRepository imageCompressionRepository) {
        this.placeRepository = placeRepository;
        this.storageService = storageService;
        this.caesiumClient = caesiumClient;
        this.caesiumProperties = caesiumProperties;
        this.objectMapper = objectMapper;
        this.storedImageCompressor = storedImageCompressor;
        this.imageCompressionRepository = imageCompressionRepository;
        AtomicInteger threadNumber = new AtomicInteger();
        this.imageExecutor = Executors.newFixedThreadPool(PARALLEL_IMAGES, runnable -> {
            Thread thread = new Thread(runnable, "place-image-compress-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        imageExecutor.shutdownNow();
    }

    /** Options with the {@code caesium.*} defaults filled in. */
    public record Options(int quality, int maxLongEdge, int minSavingsPercent) {
    }

    /** A single image compressed in memory for the console to look at; nothing is written. */
    public record Preview(byte[] webp, long originalBytes) {
    }

    public Options resolveOptions(CompressPlaceImagesRequest request) {
        Integer quality = request == null ? null : request.getQuality();
        Integer maxLongEdge = request == null ? null : request.getMaxLongEdge();
        Integer minSavings = request == null ? null : request.getMinSavingsPercent();
        return new Options(
                quality != null ? quality : caesiumProperties.getQuality(),
                maxLongEdge != null ? maxLongEdge : caesiumProperties.getMaxLongEdge(),
                minSavings != null ? minSavings : DEFAULT_MIN_SAVINGS_PERCENT);
    }

    public PlaceImageCompressionResponse getState(UUID placeId) {
        PlaceImageState state = loadState(placeId);
        if (state.getImageCompression() == null) {
            return PlaceImageCompressionResponse.builder()
                    .placeId(placeId)
                    .status(PlaceImageCompressionStatus.NOT_COMPRESSED)
                    .imageCount(imageUrls(state).size())
                    .items(List.of())
                    .build();
        }
        PlaceImageCompressionResponse recorded = readRecord(state.getImageCompression());
        recorded.setPlaceId(placeId);
        recorded.setStatus(PlaceImageCompressionStatus.COMPRESSED);
        return recorded;
    }

    public PlaceImageCompressionResponse compress(UUID placeId, CompressPlaceImagesRequest request) {
        return compress(placeId, resolveOptions(request), request != null && request.isDryRun());
    }

    /** Also the bulk run's entry point. Not transactional: the only write is the final record. */
    public PlaceImageCompressionResponse compress(UUID placeId, Options options, boolean dryRun) {
        PlaceImageState state = loadState(placeId);
        if (!dryRun && state.getImageCompression() != null) {
            throw new BusinessException(ErrorConstant.PLACE_IMAGES_ALREADY_COMPRESSED,
                    "Ảnh của place này đã được nén");
        }
        List<String> urls = imageUrls(state);
        // One query for the keys some other path (bulk run, upload hook) already compressed.
        Set<String> alreadyCompressed = imageCompressionRepository.findRecordedKeys(urls.stream()
                .map(storageService::extractObjectKey)
                .filter(java.util.Objects::nonNull)
                .toList());
        List<CompletableFuture<PlaceImageCompressionResponse.Item>> futures = urls.stream()
                .map(url -> CompletableFuture.supplyAsync(
                        () -> compressImage(url, options, dryRun, alreadyCompressed), imageExecutor))
                .toList();
        List<PlaceImageCompressionResponse.Item> items = futures.stream().map(CompletableFuture::join).toList();

        boolean incomplete = items.stream().anyMatch(item -> item.getOutcome() == PlaceImageCompressionOutcome.FAILED);
        PlaceImageCompressionResponse response = PlaceImageCompressionResponse.builder()
                .placeId(placeId)
                .status(dryRun ? PlaceImageCompressionStatus.PREVIEW
                        : incomplete ? PlaceImageCompressionStatus.INCOMPLETE
                        : PlaceImageCompressionStatus.COMPRESSED)
                .quality(options.quality())
                .maxLongEdge(options.maxLongEdge())
                .minSavingsPercent(options.minSavingsPercent())
                .imageCount(urls.size())
                .items(items)
                .build();
        for (PlaceImageCompressionResponse.Item item : items) {
            if (item.getOutcome() == PlaceImageCompressionOutcome.COMPRESSED
                    || item.getOutcome() == PlaceImageCompressionOutcome.WOULD_COMPRESS) {
                response.setOriginalBytes(response.getOriginalBytes() + item.getOriginalBytes());
                response.setCompressedBytes(response.getCompressedBytes() + item.getCompressedBytes());
            }
        }
        if (!dryRun && !incomplete) {
            response.setCompressedAt(Instant.now());
            if (!placeRepository.recordImageCompression(placeId, writeRecord(response))) {
                log.info("Place {} image compression was recorded by another request meanwhile", placeId);
            }
        }
        log.info("Place {} image compression {}: {} images, {} -> {} bytes (q={}, edge={})",
                placeId, response.getStatus(), urls.size(), response.getOriginalBytes(),
                response.getCompressedBytes(), options.quality(), options.maxLongEdge());
        return response;
    }

    /** Compresses one of the place's images in memory with the given options, writing nothing. */
    public Preview preview(UUID placeId, String url, Integer quality, Integer maxLongEdge) {
        PlaceImageState state = loadState(placeId);
        String key = url == null ? null : storageService.extractObjectKey(url.trim());
        if (key == null || !imageUrls(state).contains(url.trim())) {
            throw new BusinessException(ErrorConstant.PLACE_IMAGE_NOT_IN_PLACE,
                    "URL không phải ảnh của place này trên storage của hệ thống");
        }
        byte[] original = storageService.readObject(key);
        if (original == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Không còn file ảnh trên storage");
        }
        Options options = resolveOptions(null);
        byte[] webp = caesiumClient.compressToWebp(original,
                quality != null ? quality : options.quality(),
                maxLongEdge != null ? maxLongEdge : options.maxLongEdge());
        return new Preview(webp, original.length);
    }

    private PlaceImageCompressionResponse.Item compressImage(String url, Options options, boolean dryRun,
                                                            Set<String> alreadyCompressed) {
        PlaceImageCompressionResponse.Item.ItemBuilder item = PlaceImageCompressionResponse.Item.builder().url(url);
        String key = storageService.extractObjectKey(url);
        if (key == null) {
            return item.outcome(PlaceImageCompressionOutcome.EXTERNAL).build();
        }
        if (alreadyCompressed.contains(key)) {
            return item.outcome(PlaceImageCompressionOutcome.ALREADY_COMPRESSED).build();
        }
        // Place images are system content: never the FREE tier. A key outside every group
        // (a place pointing at an older folder) is still ours, so it is compressed as PLACES.
        StoredImageCompressor.Result result = storedImageCompressor.compressStored(new StoredImageCompressor.Request(
                key, ImageCompressionGroup.forKey(key).orElse(ImageCompressionGroup.PLACES), ImageCompressionTier.PRO,
                options.quality(), options.maxLongEdge(), options.minSavingsPercent(),
                ImageCompressionSource.PLACE, dryRun));
        return item.outcome(switch (result.outcome()) {
                    case COMPRESSED -> PlaceImageCompressionOutcome.COMPRESSED;
                    case WOULD_COMPRESS -> PlaceImageCompressionOutcome.WOULD_COMPRESS;
                    case NO_SAVINGS -> PlaceImageCompressionOutcome.NO_SAVINGS;
                    case NOT_IMAGE -> PlaceImageCompressionOutcome.NOT_IMAGE;
                    case MISSING -> PlaceImageCompressionOutcome.MISSING;
                    case ALREADY_DONE -> PlaceImageCompressionOutcome.ALREADY_COMPRESSED;
                    case FAILED -> PlaceImageCompressionOutcome.FAILED;
                })
                .originalBytes(result.originalBytes())
                .compressedBytes(result.compressedBytes())
                .message(result.message())
                .build();
    }

    private PlaceImageState loadState(UUID placeId) {
        return placeRepository.findImageState(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found"));
    }

    /** Thumbnail first, then each {@code images[].image}, without duplicates or blanks. */
    List<String> imageUrls(PlaceImageState state) {
        Set<String> urls = new LinkedHashSet<>();
        addUrl(urls, state.getThumbnail());
        String images = state.getImages();
        if (images != null && !images.isBlank()) {
            try {
                JsonNode root = objectMapper.readTree(images);
                if (root != null && root.isArray()) {
                    for (JsonNode node : root) {
                        addUrl(urls, node.isTextual() ? node.asText() : node.path("image").asText(null));
                    }
                }
            } catch (JsonProcessingException e) {
                log.warn("Place {} has unreadable images JSON; compressing the thumbnail only", state.getId());
            }
        }
        return new ArrayList<>(urls);
    }

    private static void addUrl(Set<String> urls, String url) {
        if (url != null && !url.isBlank()) {
            urls.add(url.trim());
        }
    }

    private String writeRecord(PlaceImageCompressionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise the image compression record", e);
        }
    }

    private PlaceImageCompressionResponse readRecord(String json) {
        try {
            return objectMapper.readValue(json, PlaceImageCompressionResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unreadable places.image_compression record", e);
        }
    }
}
