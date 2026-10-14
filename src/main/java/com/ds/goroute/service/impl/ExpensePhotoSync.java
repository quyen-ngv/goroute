package com.ds.goroute.service.impl;

import com.ds.goroute.entity.Expense;
import com.ds.goroute.entity.MediaAsset;
import com.ds.goroute.repository.MediaAssetRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Receipt photos of an expense, kept in media_assets; shared by trip and personal expenses. */
final class ExpensePhotoSync {

    static final String ENTITY_TYPE = "EXPENSE";

    private ExpensePhotoSync() {
    }

    /**
     * Brings the expense's media_assets rows in line with its url list.
     *
     * <p>Only the difference is written: a url that already has a row keeps it,
     * and with it whatever capture date and coordinates were read off the file.
     * Rewriting every row on each save would quietly erase that metadata the
     * first time somebody edited an unrelated field. A null list leaves the photos alone.
     */
    static void sync(MediaAssetRepository mediaAssetRepository, Expense expense, List<String> urls, UUID userId) {
        if (urls == null) return;

        List<String> wanted = urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(String::trim)
                .distinct()
                .toList();

        List<MediaAsset> existing = mediaAssetRepository.findByEntity(ENTITY_TYPE, expense.getId());
        for (MediaAsset asset : existing) {
            if (asset.getAssetRole() != null && !"PHOTO".equalsIgnoreCase(asset.getAssetRole())) {
                continue;
            }
            if (!wanted.contains(asset.getUrl())) {
                mediaAssetRepository.softDelete(asset.getId());
            }
        }

        for (int index = 0; index < wanted.size(); index++) {
            String url = wanted.get(index);
            Optional<MediaAsset> existingAsset = existing.stream()
                    .filter(asset -> ("PHOTO".equalsIgnoreCase(asset.getAssetRole())
                            || asset.getAssetRole() == null)
                            && url.equals(asset.getUrl()))
                    .findFirst();
            if (existingAsset.isPresent()) {
                existingAsset.get().setPosition(index);
                mediaAssetRepository.updatePosition(existingAsset.get());
                continue;
            }
            mediaAssetRepository.insert(MediaAsset.builder()
                    .id(UUID.randomUUID())
                    .tripId(expense.getTripId())
                    .activityId(expense.getActivityId())
                    .entityType(ENTITY_TYPE)
                    .entityId(expense.getId())
                    .mediaType("IMAGE")
                    .assetRole("PHOTO")
                    .position(index)
                    .url(url)
                    .uploadedBy(userId)
                    .build());
        }
    }
}
