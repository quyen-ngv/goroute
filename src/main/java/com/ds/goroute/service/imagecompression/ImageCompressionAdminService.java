package com.ds.goroute.service.imagecompression;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.ImageCompressionOverviewResponse;
import com.ds.goroute.dto.response.ImageCompressionSampleResponse;
import com.ds.goroute.entity.ImageCompressionGroupTotal;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.ImageCompressionRepository;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.thirdparty.caesium.CaesiumClient;
import com.ds.goroute.type.ImageCompressionGroup;
import com.ds.goroute.type.ImageCompressionOutcome;
import com.ds.goroute.type.ImageCompressionTier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** The console page's reads: settings, per-group totals, samples and the single-image preview. */
@Service
public class ImageCompressionAdminService {

    public static final int MAX_SAMPLES = 24;
    /** Pages of 500 keys looked at for samples, so a mostly compressed group cannot scan forever. */
    private static final int SAMPLE_PAGES = 4;
    private static final int PAGE_SIZE = 500;

    private final ImageCompressionRepository repository;
    private final StorageService storageService;
    private final CaesiumClient caesiumClient;
    private final ImageCompressionSettingsService settingsService;
    private final ImageOwnerTierResolver tierResolver;

    public ImageCompressionAdminService(ImageCompressionRepository repository,
                                        StorageService storageService,
                                        CaesiumClient caesiumClient,
                                        ImageCompressionSettingsService settingsService,
                                        ImageOwnerTierResolver tierResolver) {
        this.repository = repository;
        this.storageService = storageService;
        this.caesiumClient = caesiumClient;
        this.settingsService = settingsService;
        this.tierResolver = tierResolver;
    }

    /** A compressed preview of one stored image, with what produced it. */
    public record Preview(byte[] webp, long originalBytes, ImageCompressionTier tier, int quality, int maxLongEdge) {
    }

    public ImageCompressionOverviewResponse overview() {
        ImageCompressionSettingsService.Settings settings = settingsService.current();
        List<ImageCompressionGroupTotal> totals = repository.totalsByGroup();
        List<ImageCompressionOverviewResponse.Group> groups = Arrays.stream(ImageCompressionGroup.values())
                .map(group -> groupTotals(group, totals))
                .toList();
        return ImageCompressionOverviewResponse.builder()
                .autoEnabled(settings.autoEnabled())
                .freeQuality(settings.free().quality())
                .freeMaxLongEdge(settings.free().maxLongEdge())
                .proQuality(settings.pro().quality())
                .proMaxLongEdge(settings.pro().maxLongEdge())
                .minSavingsPercent(settings.minSavingsPercent())
                .groups(groups)
                .build();
    }

    /** Up to {@code limit} images of the group not compressed yet, in key order. */
    public List<ImageCompressionSampleResponse> samples(ImageCompressionGroup group, int limit) {
        int wanted = Math.max(1, Math.min(limit, MAX_SAMPLES));
        List<ImageCompressionSampleResponse> samples = new ArrayList<>();
        String startAfter = null;
        for (int pageNumber = 0; pageNumber < SAMPLE_PAGES && samples.size() < wanted; pageNumber++) {
            List<StorageService.ObjectEntry> page = storageService.listObjectPage(group.prefix(), startAfter, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            startAfter = page.get(page.size() - 1).key();
            List<StorageService.ObjectEntry> images = page.stream()
                    .filter(entry -> StoredImageCompressor.hasImageExtension(entry.key()))
                    .toList();
            Set<String> done = repository.findRecordedKeys(images.stream().map(StorageService.ObjectEntry::key).toList());
            for (StorageService.ObjectEntry entry : images) {
                if (samples.size() >= wanted) {
                    break;
                }
                if (!done.contains(entry.key())) {
                    samples.add(ImageCompressionSampleResponse.builder()
                            .key(entry.key())
                            .url(storageService.urlFor(entry.key()))
                            .bytes(entry.size())
                            .tier(tierResolver.forKey(group, entry.key()))
                            .build());
                }
            }
        }
        return samples;
    }

    /**
     * Compresses one stored image in memory with its tier's settings, or the given overrides,
     * writing nothing. Only image files of a compression group may be read this way.
     */
    public Preview preview(String key, Integer quality, Integer maxLongEdge) {
        ImageCompressionGroup group = ImageCompressionGroup.forKey(key)
                .filter(found -> StoredImageCompressor.hasImageExtension(key))
                .orElseThrow(() -> new BusinessException(ErrorConstant.IMAGE_COMPRESSION_KEY_NOT_ALLOWED,
                        "Không phải file ảnh thuộc nhóm nén ảnh nào"));
        byte[] original = storageService.readObject(key);
        if (original == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Không còn file ảnh trên storage");
        }
        ImageCompressionTier tier = tierResolver.forKey(group, key);
        ImageCompressionSettingsService.TierSettings tierSettings = settingsService.current().forTier(tier);
        int usedQuality = quality != null ? quality : tierSettings.quality();
        int usedEdge = maxLongEdge != null ? maxLongEdge : tierSettings.maxLongEdge();
        return new Preview(caesiumClient.compressToWebp(original, usedQuality, usedEdge),
                original.length, tier, usedQuality, usedEdge);
    }

    private static ImageCompressionOverviewResponse.Group groupTotals(ImageCompressionGroup group,
                                                                      List<ImageCompressionGroupTotal> totals) {
        ImageCompressionOverviewResponse.Group.GroupBuilder row = ImageCompressionOverviewResponse.Group.builder()
                .group(group)
                .prefix(group.prefix())
                .ownerInKey(group.ownerInKey());
        long compressed = 0;
        long noSavings = 0;
        long notImages = 0;
        long originalBytes = 0;
        long compressedBytes = 0;
        for (ImageCompressionGroupTotal total : totals) {
            if (!group.name().equals(total.getGroupCode())) {
                continue;
            }
            if (ImageCompressionOutcome.COMPRESSED.name().equals(total.getOutcome())) {
                compressed += total.getImageCount();
                originalBytes += total.getOriginalBytes();
                compressedBytes += total.getCompressedBytes();
            } else if (ImageCompressionOutcome.NO_SAVINGS.name().equals(total.getOutcome())) {
                noSavings += total.getImageCount();
            } else if (ImageCompressionOutcome.NOT_IMAGE.name().equals(total.getOutcome())) {
                notImages += total.getImageCount();
            }
        }
        return row.compressed(compressed).noSavings(noSavings).notImages(notImages)
                .originalBytes(originalBytes).compressedBytes(compressedBytes).build();
    }
}
