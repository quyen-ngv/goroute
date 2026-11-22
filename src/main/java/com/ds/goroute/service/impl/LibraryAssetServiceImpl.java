package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateLibraryAssetRequest;
import com.ds.goroute.dto.request.UpdateLibraryAssetRequest;
import com.ds.goroute.dto.response.LibraryAssetResponse;
import com.ds.goroute.dto.response.LibraryAssetUsageResponse;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.entity.FoodAssetUsageRow;
import com.ds.goroute.entity.LibraryAsset;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.FoodRepository;
import com.ds.goroute.repository.LibraryAssetRepository;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.LibraryAssetService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.AssetCollection;
import com.ds.goroute.type.AssetSource;
import com.ds.goroute.type.AssetStatus;
import com.ds.goroute.utils.JsonUtils;
import com.ds.goroute.utils.LibraryAssetNameResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LibraryAssetServiceImpl implements LibraryAssetService {

    /** Storage prefix of library images; the orphan sweep frees replaced ones (ImageStorageCleanupService). */
    static final String STORAGE_PREFIX = "asset-library";

    private static final String USAGE_TYPE_FOOD = "FOOD";

    private final LibraryAssetRepository libraryAssetRepository;
    private final FoodRepository foodRepository;
    private final FileUploadService fileUploadService;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<LibraryAssetResponse> adminSearch(AssetCollection collection, AssetStatus status, String q,
                                                          boolean missingImage, int page, int size) {
        String collectionName = collection == null ? null : collection.name();
        String statusName = status == null ? null : status.name();
        String query = q == null || q.isBlank() ? null : q.trim();
        List<LibraryAsset> assets = libraryAssetRepository.search(
                collectionName, statusName, query, missingImage, size, page * size);
        long total = libraryAssetRepository.count(collectionName, statusName, query, missingImage);

        Map<UUID, Long> usageCounts = foodRepository.findIngredientAssetUsages(ingredientIds(assets)).stream()
                .collect(Collectors.groupingBy(FoodAssetUsageRow::getAssetId, Collectors.counting()));
        List<LibraryAssetResponse> items = assets.stream()
                .map(asset -> {
                    LibraryAssetResponse response = toResponse(asset);
                    if (isIngredient(asset)) {
                        response.setUsageCount(usageCounts.getOrDefault(asset.getId(), 0L).intValue());
                    }
                    return response;
                })
                .toList();
        return PageResponse.of(items, total, page, size);
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryAssetResponse adminGet(UUID assetId) {
        LibraryAsset asset = requireAsset(assetId);
        LibraryAssetResponse response = toResponse(asset);
        List<LibraryAssetUsageResponse> usedBy = findUsages(asset);
        response.setUsedBy(usedBy);
        response.setUsageCount(usedBy.size());
        return response;
    }

    @Override
    @Transactional
    public LibraryAssetResponse adminCreate(CreateLibraryAssetRequest request, UUID actorUserId) {
        String collection = request.getCollection().name();
        String key = request.getKey().trim();
        ensureKeyFree(collection, key, null);
        String imageUrl = requireOwnImageUrl(blankToNull(request.getImageUrl()));
        LocalDateTime now = LocalDateTime.now();
        LibraryAsset asset = LibraryAsset.builder()
                .id(UUID.randomUUID())
                .collection(collection)
                .assetKey(key)
                .nameEn(request.getNameEn().trim())
                .nameVi(blankToNull(request.getNameVi()))
                .nameJa(blankToNull(request.getNameJa()))
                .nameKo(blankToNull(request.getNameKo()))
                .description(blankToNull(request.getDescription()))
                .tags(toTagsJson(request.getTags()))
                .imageUrl(imageUrl)
                .source((request.getSource() == null ? AssetSource.UNKNOWN : request.getSource()).name())
                .sourceUrl(blankToNull(request.getSourceUrl()))
                .licenseNote(blankToNull(request.getLicenseNote()))
                .status(AssetStatus.ACTIVE.name())
                .version(imageUrl == null ? 0 : 1)
                .createdBy(actorUserId)
                .updatedBy(actorUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            libraryAssetRepository.insert(asset);
        } catch (DuplicateKeyException exception) {
            throw keyTaken(key);
        }
        return toResponse(asset);
    }

    @Override
    @Transactional
    public LibraryAssetResponse adminUpdate(UUID assetId, UpdateLibraryAssetRequest request, UUID actorUserId) {
        LibraryAsset asset = requireAsset(assetId);
        if (request.getKey() != null && !request.getKey().trim().equals(asset.getAssetKey())) {
            String key = request.getKey().trim();
            ensureKeyFree(asset.getCollection(), key, assetId);
            asset.setAssetKey(key);
        }
        if (request.getNameEn() != null) asset.setNameEn(request.getNameEn().trim());
        if (request.getNameVi() != null) asset.setNameVi(blankToNull(request.getNameVi()));
        if (request.getNameJa() != null) asset.setNameJa(blankToNull(request.getNameJa()));
        if (request.getNameKo() != null) asset.setNameKo(blankToNull(request.getNameKo()));
        if (request.getDescription() != null) asset.setDescription(blankToNull(request.getDescription()));
        if (request.getTags() != null) asset.setTags(toTagsJson(request.getTags()));
        if (request.getImageUrl() != null) {
            setImage(asset, requireOwnImageUrl(blankToNull(request.getImageUrl())));
        }
        if (request.getSource() != null) asset.setSource(request.getSource().name());
        if (request.getSourceUrl() != null) asset.setSourceUrl(blankToNull(request.getSourceUrl()));
        if (request.getLicenseNote() != null) asset.setLicenseNote(blankToNull(request.getLicenseNote()));
        if (request.getStatus() != null) asset.setStatus(request.getStatus().name());
        save(asset, actorUserId);
        return toResponse(asset);
    }

    @Override
    @Transactional
    public LibraryAssetResponse adminReplaceImage(UUID assetId, MultipartFile file, UUID actorUserId) {
        LibraryAsset asset = requireAsset(assetId);
        String prefix = STORAGE_PREFIX + "/" + asset.getCollection().toLowerCase(Locale.ROOT);
        ImageUploadOutcome outcome = fileUploadService.uploadImage(
                ImageUploadRequest.of(actorUserId, ImageUploadRequest.ImageEntryPoint.ASSET_LIBRARY, prefix), file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection() ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }
        // The old file stays until the orphan sweep sees nothing naming it (14 days at least).
        setImage(asset, outcome.url());
        save(asset, actorUserId);
        return toResponse(asset);
    }

    @Override
    @Transactional
    public void adminDelete(UUID assetId) {
        LibraryAsset asset = requireAsset(assetId);
        List<LibraryAssetUsageResponse> usages = findUsages(asset);
        if (!usages.isEmpty()) {
            throw new BusinessException(ErrorConstant.ASSET_LIBRARY_IN_USE,
                    "Asset is used by " + usages.size() + " record(s); archive it or remove the references first");
        }
        libraryAssetRepository.delete(assetId);
    }

    private void setImage(LibraryAsset asset, String imageUrl) {
        boolean changed = imageUrl == null ? asset.getImageUrl() != null : !imageUrl.equals(asset.getImageUrl());
        asset.setImageUrl(imageUrl);
        if (changed) {
            asset.setVersion((asset.getVersion() == null ? 0 : asset.getVersion()) + 1);
        }
    }

    private void save(LibraryAsset asset, UUID actorUserId) {
        asset.setUpdatedBy(actorUserId);
        asset.setUpdatedAt(LocalDateTime.now());
        try {
            if (libraryAssetRepository.update(asset) != 1) {
                throw notFound();
            }
        } catch (DuplicateKeyException exception) {
            throw keyTaken(asset.getAssetKey());
        }
    }

    private List<LibraryAssetUsageResponse> findUsages(LibraryAsset asset) {
        if (!isIngredient(asset)) {
            return List.of();
        }
        return foodRepository.findIngredientAssetUsages(List.of(asset.getId())).stream()
                .map(row -> LibraryAssetUsageResponse.builder()
                        .type(USAGE_TYPE_FOOD)
                        .id(row.getFoodId())
                        .name(row.getNameVi() != null && !row.getNameVi().isBlank() ? row.getNameVi() : row.getNameEn())
                        .build())
                .toList();
    }

    private static Set<UUID> ingredientIds(List<LibraryAsset> assets) {
        return assets.stream()
                .filter(LibraryAssetServiceImpl::isIngredient)
                .map(LibraryAsset::getId)
                .collect(Collectors.toSet());
    }

    private static boolean isIngredient(LibraryAsset asset) {
        return AssetCollection.INGREDIENT.name().equals(asset.getCollection());
    }

    /**
     * Only images already in our own storage are accepted by URL. Anything else must come
     * through the upload endpoint, so no image reaches the product without the upload checks.
     */
    private String requireOwnImageUrl(String imageUrl) {
        if (imageUrl != null && storageService.extractObjectKey(imageUrl) == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "imageUrl must point to our own storage; upload external images instead");
        }
        return imageUrl;
    }

    private void ensureKeyFree(String collection, String key, UUID exceptAssetId) {
        libraryAssetRepository.findByCollectionAndKey(collection, key)
                .filter(existing -> !existing.getId().equals(exceptAssetId))
                .ifPresent(existing -> {
                    throw keyTaken(key);
                });
    }

    private LibraryAsset requireAsset(UUID assetId) {
        return libraryAssetRepository.findById(assetId).orElseThrow(LibraryAssetServiceImpl::notFound);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.ASSET_LIBRARY_NOT_FOUND, "Library asset not found");
    }

    private static BusinessException keyTaken(String key) {
        return new BusinessException(ErrorConstant.ASSET_LIBRARY_KEY_TAKEN,
                "Another asset of this collection already uses the key '" + key + "'");
    }

    private LibraryAssetResponse toResponse(LibraryAsset asset) {
        return LibraryAssetResponse.builder()
                .id(asset.getId())
                .collection(asset.getCollection())
                .key(asset.getAssetKey())
                .name(LibraryAssetNameResolver.resolveName(asset))
                .names(LibraryAssetNameResolver.allNames(asset))
                .description(asset.getDescription())
                .tags(parseTags(asset.getTags()))
                .imageUrl(asset.getImageUrl())
                .source(asset.getSource())
                .sourceUrl(asset.getSourceUrl())
                .licenseNote(asset.getLicenseNote())
                .status(asset.getStatus())
                .version(asset.getVersion())
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }

    private static List<String> parseTags(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        List<String> tags = JsonUtils.fromJson(json, new TypeReference<List<String>>() {});
        return tags == null ? List.of() : tags;
    }

    private static String toTagsJson(List<String> tags) {
        if (tags == null) {
            return null;
        }
        List<String> cleaned = tags.stream()
                .filter(tag -> tag != null && !tag.isBlank())
                .map(tag -> tag.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        return JsonUtils.toJson(cleaned);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
