package com.ds.goroute.service.imagecompression;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.ImageCompressionRunResponse;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ImageCompressionRepository;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionOutcome;
import com.ds.goroute.type.ImageCompressionRunStatus;
import com.ds.goroute.type.ImageCompressionSource;
import com.ds.goroute.type.ImageCompressionTier;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The console's bulk run (epic 00 §9b): walks the chosen storage groups object by object, in key
 * order, and compresses every image not yet in {@code image_compressions} with the tier settings
 * of its owner. The run lives in this JVM only; a deploy ends it and the next run skips what was
 * recorded, so it carries on where it stopped.
 */
@Slf4j
@Component
public class ImageCompressionRunner {

    private static final int PAGE_SIZE = 500;
    /** Matches the caesium service's slots (MAX_CONCURRENT). */
    private static final int PARALLEL_IMAGES = 2;
    /** This many failed images in a row almost always means the caesium service is down. */
    private static final int MAX_CONSECUTIVE_FAILURES = 20;
    private static final int MAX_RECENT_ERRORS = 20;

    private final StorageService storageService;
    private final ImageCompressionRepository repository;
    private final StoredImageCompressor compressor;
    private final ImageCompressionSettingsService settingsService;
    private final ImageOwnerTierResolver tierResolver;
    private final ExecutorService coordinator = Executors.newSingleThreadExecutor(daemon("image-compress-run"));
    private final ExecutorService imagePool = Executors.newFixedThreadPool(PARALLEL_IMAGES, daemon("image-compress-worker"));

    /** Guarded by {@code this}. */
    private Run run;

    public ImageCompressionRunner(StorageService storageService,
                                  ImageCompressionRepository repository,
                                  StoredImageCompressor compressor,
                                  ImageCompressionSettingsService settingsService,
                                  ImageOwnerTierResolver tierResolver) {
        this.storageService = storageService;
        this.repository = repository;
        this.compressor = compressor;
        this.settingsService = settingsService;
        this.tierResolver = tierResolver;
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        AtomicInteger number = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, name + "-" + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    @PreDestroy
    void shutdown() {
        coordinator.shutdownNow();
        imagePool.shutdownNow();
    }

    /** Starts a run over {@code groups}, or every group when empty. */
    public synchronized ImageCompressionRunResponse start(List<ImageCompressionGroup> groups) {
        if (run != null && run.isActive()) {
            throw new BusinessException(ErrorConstant.IMAGE_COMPRESSION_RUN_ACTIVE, "Đang có một lượt nén ảnh chạy");
        }
        List<ImageCompressionGroup> chosen = groups == null || groups.isEmpty()
                ? List.of(ImageCompressionGroup.values())
                : groups.stream().distinct().toList();
        Run started = new Run(chosen, settingsService.current());
        run = started;
        coordinator.submit(() -> execute(started));
        log.info("Image compression run started over {}", chosen);
        return started.snapshot();
    }

    public ImageCompressionRunResponse current() {
        Run current;
        synchronized (this) {
            current = run;
        }
        return current == null
                ? ImageCompressionRunResponse.builder()
                        .status(ImageCompressionRunStatus.IDLE)
                        .groups(List.of())
                        .recentErrors(List.of())
                        .build()
                : current.snapshot();
    }

    public ImageCompressionRunResponse cancel() {
        Run current;
        synchronized (this) {
            current = run;
        }
        if (current != null) {
            current.requestCancel();
        }
        return current();
    }

    private void execute(Run state) {
        try {
            for (ImageCompressionGroup group : state.groups) {
                if (state.isCancelRequested() || state.isStopped()) {
                    break;
                }
                state.beginGroup(group);
                runGroup(state, group);
            }
            state.finish(state.isCancelRequested() ? ImageCompressionRunStatus.CANCELLED
                    : state.isStopped() ? ImageCompressionRunStatus.STOPPED
                    : ImageCompressionRunStatus.COMPLETED, null);
        } catch (Exception e) {
            log.error("Image compression run stopped", e);
            state.finish(ImageCompressionRunStatus.STOPPED, e.getMessage());
        }
    }

    private void runGroup(Run state, ImageCompressionGroup group) {
        String startAfter = null;
        while (!state.isCancelRequested() && !state.isStopped()) {
            List<StorageService.ObjectEntry> page = storageService.listObjectPage(group.prefix(), startAfter, PAGE_SIZE);
            if (page.isEmpty()) {
                return;
            }
            startAfter = page.get(page.size() - 1).key();
            List<StorageService.ObjectEntry> images = page.stream()
                    .filter(entry -> StoredImageCompressor.hasImageExtension(entry.key()))
                    .toList();
            Set<String> done = repository.findRecordedKeys(images.stream().map(StorageService.ObjectEntry::key).toList());
            state.listed(page.size(), images.size(), done.size());

            List<CompletableFuture<Void>> tasks = new ArrayList<>();
            for (StorageService.ObjectEntry entry : images) {
                if (done.contains(entry.key())) {
                    continue;
                }
                tasks.add(CompletableFuture.runAsync(() -> compressOne(state, group, entry), imagePool));
            }
            CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
        }
    }

    private void compressOne(Run state, ImageCompressionGroup group, StorageService.ObjectEntry entry) {
        if (state.isCancelRequested() || state.isStopped()) {
            return;
        }
        StoredImageCompressor.Result result;
        if (entry.size() > StoredImageCompressor.MAX_INPUT_BYTES) {
            result = new StoredImageCompressor.Result(entry.key(), ImageCompressionOutcome.FAILED, entry.size(), null,
                    "Ảnh lớn hơn " + (StoredImageCompressor.MAX_INPUT_BYTES / 1024 / 1024) + " MB");
        } else {
            ImageCompressionTier tier = tierResolver.forKey(group, entry.key());
            ImageCompressionSettingsService.TierSettings tierSettings = state.settings.forTier(tier);
            result = compressor.compressStored(new StoredImageCompressor.Request(entry.key(), group, tier,
                    tierSettings.quality(), tierSettings.maxLongEdge(), state.settings.minSavingsPercent(),
                    ImageCompressionSource.BULK, false));
        }
        state.record(result);
    }

    /** One run's counters. Written by the worker threads, read by request threads. */
    private static final class Run {
        private final List<ImageCompressionGroup> groups;
        private final ImageCompressionSettingsService.Settings settings;
        private final Instant startedAt = Instant.now();
        private final Deque<String> recentErrors = new ArrayDeque<>();
        private ImageCompressionRunStatus status = ImageCompressionRunStatus.RUNNING;
        private boolean cancelRequested;
        private boolean stopped;
        private Instant finishedAt;
        private ImageCompressionGroup currentGroup;
        private int finishedGroups;
        private long listedObjects;
        private long images;
        private long alreadyDone;
        private long compressed;
        private long noSavings;
        private long notImages;
        private long missing;
        private long failed;
        private long originalBytes;
        private long compressedBytes;
        private int consecutiveFailures;
        private String message;

        private Run(List<ImageCompressionGroup> groups, ImageCompressionSettingsService.Settings settings) {
            this.groups = groups;
            this.settings = settings;
        }

        synchronized boolean isActive() {
            return status == ImageCompressionRunStatus.RUNNING || status == ImageCompressionRunStatus.CANCELLING;
        }

        synchronized boolean isCancelRequested() {
            return cancelRequested;
        }

        synchronized boolean isStopped() {
            return stopped;
        }

        synchronized void requestCancel() {
            if (isActive()) {
                cancelRequested = true;
                status = ImageCompressionRunStatus.CANCELLING;
            }
        }

        synchronized void beginGroup(ImageCompressionGroup group) {
            if (currentGroup != null) {
                finishedGroups++;
            }
            currentGroup = group;
        }

        synchronized void listed(int objects, int imageCount, int recorded) {
            listedObjects += objects;
            images += imageCount;
            alreadyDone += recorded;
        }

        synchronized void record(StoredImageCompressor.Result result) {
            switch (result.outcome()) {
                case COMPRESSED -> {
                    compressed++;
                    originalBytes += result.originalBytes();
                    compressedBytes += result.compressedBytes();
                }
                case NO_SAVINGS -> noSavings++;
                case NOT_IMAGE -> notImages++;
                case MISSING -> missing++;
                default -> {
                }
            }
            if (result.outcome() == ImageCompressionOutcome.FAILED) {
                failed++;
                consecutiveFailures++;
                recentErrors.addLast(result.key() + ": " + result.message());
                while (recentErrors.size() > MAX_RECENT_ERRORS) {
                    recentErrors.removeFirst();
                }
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES && !stopped) {
                    stopped = true;
                    message = MAX_CONSECUTIVE_FAILURES + " ảnh liên tiếp bị lỗi, kiểm tra service caesium";
                }
            } else {
                consecutiveFailures = 0;
            }
        }

        synchronized void finish(ImageCompressionRunStatus finalStatus, String reason) {
            status = finalStatus;
            if (reason != null) {
                message = reason;
            }
            if (currentGroup != null) {
                finishedGroups++;
            }
            currentGroup = null;
            finishedAt = Instant.now();
            log.info("Image compression run {}: {} listed, {} images, {} already done, {} compressed, {} no savings, {} failed, {} -> {} bytes",
                    finalStatus, listedObjects, images, alreadyDone, compressed, noSavings, failed,
                    originalBytes, compressedBytes);
        }

        synchronized ImageCompressionRunResponse snapshot() {
            return ImageCompressionRunResponse.builder()
                    .status(status)
                    .groups(groups)
                    .currentGroup(currentGroup)
                    .finishedGroups(finishedGroups)
                    .startedAt(startedAt)
                    .finishedAt(finishedAt)
                    .freeQuality(settings.free().quality())
                    .freeMaxLongEdge(settings.free().maxLongEdge())
                    .proQuality(settings.pro().quality())
                    .proMaxLongEdge(settings.pro().maxLongEdge())
                    .minSavingsPercent(settings.minSavingsPercent())
                    .listedObjects(listedObjects)
                    .images(images)
                    .alreadyDone(alreadyDone)
                    .compressed(compressed)
                    .noSavings(noSavings)
                    .notImages(notImages)
                    .missing(missing)
                    .failed(failed)
                    .originalBytes(originalBytes)
                    .compressedBytes(compressedBytes)
                    .message(message)
                    .recentErrors(new ArrayList<>(recentErrors))
                    .build();
        }
    }
}
