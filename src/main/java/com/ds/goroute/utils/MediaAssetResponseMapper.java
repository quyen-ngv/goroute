package com.ds.goroute.utils;

import com.ds.goroute.dto.response.MemoryImageResponse;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.entity.User;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns {@link MediaAsset} rows into the V2 image payload.
 *
 * <p>Every surface that returns images — trip memories, activity memories,
 * expense receipts — now reads from {@code media_assets} and builds its list
 * here, so a field added to the payload appears everywhere at once instead of
 * in whichever endpoint someone remembered.
 */
public final class MediaAssetResponseMapper {
    private MediaAssetResponseMapper() {
    }

    /**
     * Images only, de-duplicated by url, in the order given.
     *
     * <p>Videos are skipped: the V2 payload has always described photographs,
     * and a client rendering it as a gallery would show a broken tile.
     *
     * <p>Uploader names and activity names are left null. Resolving them costs a
     * query the caller may not want to pay for, so the caller that does want them
     * looks them up in bulk and calls
     * {@link #toImageResponses(List, Map, Map)} instead.
     */
    public static List<MemoryImageResponse> toImageResponses(List<MediaAsset> assets) {
        return toImageResponses(assets, Map.of(), Map.of());
    }

    /**
     * As {@link #toImageResponses(List)}, with the names behind the ids filled in.
     *
     * @param uploaders     uploader id to user, for the name and avatar shown beside a photo
     * @param activityNames activity id to activity name, naming the stop a photo belongs to
     */
    public static List<MemoryImageResponse> toImageResponses(List<MediaAsset> assets,
                                                             Map<UUID, User> uploaders,
                                                             Map<UUID, String> activityNames) {
        Set<String> seenUrls = new LinkedHashSet<>();
        List<MemoryImageResponse> images = new ArrayList<>();
        if (assets == null) return images;

        Map<UUID, User> uploadersById = uploaders == null ? Map.of() : uploaders;
        Map<UUID, String> namesByActivityId = activityNames == null ? Map.of() : activityNames;

        for (MediaAsset asset : assets) {
            if (asset.getMediaType() != null && "VIDEO".equalsIgnoreCase(asset.getMediaType())) {
                continue;
            }
            User uploader = asset.getUploadedBy() == null ? null : uploadersById.get(asset.getUploadedBy());
            String activityName = asset.getActivityId() == null
                    ? null
                    : namesByActivityId.get(asset.getActivityId());
            MemoryImageUrlNormalizer.normalize(asset.getUrl())
                    .filter(seenUrls::add)
                    .ifPresent(url -> images.add(MemoryImageResponse.builder()
                            .id(asset.getId())
                            .url(url)
                            .entityType(asset.getEntityType())
                            .entityId(asset.getEntityId())
                            .mediaType(asset.getMediaType())
                            .assetRole(asset.getAssetRole())
                            .position(asset.getPosition())
                            .title(asset.getCaption())
                            .description(asset.getDescription())
                            .takenAt(asset.getTakenAt())
                            .dateSource(asset.getDateSource())
                            .captureSource(asset.getCaptureSource())
                            .latitude(asset.getLatitude())
                            .longitude(asset.getLongitude())
                            .accuracyMeters(asset.getAccuracyMeters())
                            .placeId(asset.getPlaceId())
                            .locationName(asset.getLocationName())
                            .locationSource(asset.getLocationSource())
                            .createdAt(asset.getCreatedAt())
                            .uploadedBy(asset.getUploadedBy())
                            .uploaderName(uploader == null ? null : uploader.getFullName())
                            .uploaderAvatarUrl(uploader == null ? null : uploader.getAvatarUrl())
                            .activityId(asset.getActivityId())
                            .activityName(activityName)
                            .build()));
        }
        return images;
    }

    /**
     * The flat url list the pre-V2 clients read.
     *
     * <p>Built from the same rows as {@link #toImageResponses}, so the two can
     * never disagree about which images exist.
     */
    public static List<String> toUrls(List<MemoryImageResponse> images) {
        return images.stream().map(MemoryImageResponse::getUrl).toList();
    }
}
