package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.ImageCleanupMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageCleanupService {

    private final ImageCleanupMapper imageCleanupMapper;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    private final Map<String, EntitySpec> specs = buildSpecs();

    public List<String> supportedEntities() {
        return specs.keySet().stream().sorted().toList();
    }

    public DeleteRecordImagesResult deleteImagesForEntityRecord(String entity, UUID id) {
        String normalizedEntity = normalizeEntity(entity);
        Set<String> urls = collectRecordUrls(normalizedEntity, id);
        Set<String> keys = toKeys(urls);
        storageService.deleteObjectKeys(new ArrayList<>(keys));
        log.info("Deleted {} image objects for entity {} record {}", keys.size(), normalizedEntity, id);
        return new DeleteRecordImagesResult(normalizedEntity, id, urls.size(), keys.size(), new ArrayList<>(keys));
    }

    public OrphanImageCleanupResult cleanupOrphanedImages(
            Collection<String> entities,
            Collection<String> requestedPrefixes,
            boolean dryRun,
            int limit,
            boolean backupBeforeDelete,
            String backupPrefix
    ) {
        List<String> normalizedEntities = normalizeEntities(entities);
        Set<String> prefixes = resolvePrefixes(normalizedEntities, requestedPrefixes);
        Set<String> referencedKeys = collectReferencedKeys(specs.keySet());

        if (!dryRun && !backupBeforeDelete) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "backupBeforeDelete=true is required when dryRun=false"
            );
        }

        List<String> scannedKeys = prefixes.stream()
                .flatMap(prefix -> storageService.listObjectKeys(prefix).stream())
                .distinct()
                .sorted()
                .toList();

        List<String> allOrphanKeys = scannedKeys.stream()
                .filter(key -> !referencedKeys.contains(key))
                .sorted()
                .toList();
        int safeLimit = Math.max(1, limit);
        List<String> orphanKeys = allOrphanKeys.stream()
                .limit(safeLimit)
                .toList();

        int deletedCount = 0;
        String resolvedBackupPrefix = null;
        int backupCount = 0;
        if (!dryRun && !orphanKeys.isEmpty()) {
            if (backupBeforeDelete) {
                resolvedBackupPrefix = resolveBackupPrefix(backupPrefix);
                storageService.copyObjectKeys(orphanKeys, resolvedBackupPrefix);
                backupCount = orphanKeys.size();
            }
            storageService.deleteObjectKeys(orphanKeys);
            deletedCount = orphanKeys.size();
        }

        log.info(
                "Image orphan cleanup completed. dryRun={}, prefixes={}, scanned={}, referenced={}, totalOrphans={}, returnedOrphans={}, backedUp={}, deleted={}",
                dryRun,
                prefixes,
                scannedKeys.size(),
                referencedKeys.size(),
                allOrphanKeys.size(),
                orphanKeys.size(),
                backupCount,
                deletedCount
        );

        return new OrphanImageCleanupResult(
                normalizedEntities,
                new ArrayList<>(prefixes),
                dryRun,
                scannedKeys.size(),
                referencedKeys.size(),
                orphanKeys.size(),
                allOrphanKeys.size(),
                allOrphanKeys.size() > orphanKeys.size(),
                deletedCount,
                backupCount,
                resolvedBackupPrefix,
                orphanKeys
        );
    }

    private Set<String> collectReferencedKeys(Collection<String> entities) {
        Set<String> urls = new LinkedHashSet<>();
        for (String entity : entities) {
            EntitySpec spec = requireSpec(entity);
            queryRows(spec.allSql()).stream()
                    .filter(row -> row != null && !row.isEmpty())
                    .forEach(row -> urls.addAll(extractUrls(row.values())));
        }
        return toKeys(urls);
    }

    private Set<String> collectRecordUrls(String entity, UUID id) {
        Set<String> urls = new LinkedHashSet<>();

        switch (entity) {
            case "TRIP" -> {
                urls.addAll(queryUrls("SELECT cover_image_url FROM trips WHERE id = ? AND cover_image_url IS NOT NULL", id));
                urls.addAll(queryUrls("SELECT photo_url FROM activities WHERE trip_id = ? AND photo_url IS NOT NULL", id));
                urls.addAll(queryUrls("SELECT receipt_url, photo_urls FROM expenses WHERE trip_id = ? AND (receipt_url IS NOT NULL OR photo_urls IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT url FROM media_assets WHERE trip_id = ? AND url IS NOT NULL", id));
                urls.addAll(queryUrls("SELECT photo_url, thumbnail_url FROM trip_photos WHERE trip_id = ? AND (photo_url IS NOT NULL OR thumbnail_url IS NOT NULL)", id));
            }
            case "PLACE" -> {
                urls.addAll(queryUrls("SELECT thumbnail, images FROM places WHERE id = ? AND (thumbnail IS NOT NULL OR images IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT profile_picture, images FROM place_reviews WHERE place_id = ? AND (profile_picture IS NOT NULL OR images IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT photos FROM user_reviews WHERE place_id = ? AND photos IS NOT NULL", id));
            }
            case "FOOD" -> {
                urls.addAll(queryUrls("SELECT image_url, introduction_images, varieties, how_to_eat_steps FROM foods WHERE id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL OR varieties IS NOT NULL OR how_to_eat_steps IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT image_url, introduction_images FROM food_city_scores WHERE food_id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL)", id));
            }
            case "ACTIVITY" -> {
                urls.addAll(queryUrls("SELECT photo_url FROM activities WHERE id = ? AND photo_url IS NOT NULL", id));
                urls.addAll(queryUrls("SELECT receipt_url, photo_urls FROM expenses WHERE activity_id = ? AND (receipt_url IS NOT NULL OR photo_urls IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT url FROM media_assets WHERE activity_id = ? AND url IS NOT NULL", id));
                urls.addAll(queryUrls("""
                        SELECT cp.photo_url
                        FROM checkin_photos cp
                        JOIN checkins c ON c.id = cp.checkin_id
                        WHERE c.activity_id = ?
                          AND cp.photo_url IS NOT NULL
                        """, id));
            }
            default -> urls.addAll(queryUrls(requireSpec(entity).recordSql(), id));
        }

        return urls;
    }

    private List<String> queryUrls(String sql, UUID id) {
        return queryRows(sql, id).stream()
                .filter(row -> row != null && !row.isEmpty())
                .flatMap(row -> extractUrls(row.values()).stream())
                .toList();
    }

    private List<Map<String, Object>> queryRows(String sql, Object... args) {
        try {
            UUID id = args.length > 0 && args[0] instanceof UUID uuid ? uuid : null;
            return imageCleanupMapper.selectRows(sql, id);
        } catch (Exception e) {
            log.error("Image reference query failed. Cleanup stopped to avoid unsafe deletes: {}", e.getMessage());
            throw new BusinessException(
                    ErrorConstant.INTERNAL_SERVER_ERROR,
                    "Image reference query failed; cleanup aborted"
            );
        }
    }

    private Set<String> extractUrls(Collection<Object> values) {
        Set<String> urls = new LinkedHashSet<>();
        for (Object value : values) {
            extractUrls(value, urls);
        }
        return urls;
    }

    private void extractUrls(Object value, Set<String> urls) {
        if (value == null) {
            return;
        }
        if (value instanceof Array sqlArray) {
            try {
                Object array = sqlArray.getArray();
                if (array instanceof Object[] objects) {
                    for (Object item : objects) {
                        extractUrls(item, urls);
                    }
                }
            } catch (Exception e) {
                log.debug("Could not parse SQL array image refs: {}", e.getMessage());
            }
            return;
        }
        if (value instanceof Collection<?> collection) {
            collection.forEach(item -> extractUrls(item, urls));
            return;
        }

        String text = value.toString().trim();
        if (text.isBlank()) {
            return;
        }
        if (looksLikeImageReference(text)) {
            urls.add(text);
            return;
        }
        if ((text.startsWith("[") && text.endsWith("]")) || (text.startsWith("{") && text.endsWith("}"))) {
            try {
                collectJsonUrls(objectMapper.readTree(text), urls);
            } catch (Exception ignored) {
                // Not every text/json column is valid image JSON.
            }
        }
    }

    private void collectJsonUrls(JsonNode node, Set<String> urls) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (looksLikeImageReference(value)) {
                urls.add(value);
            }
            return;
        }
        if (node.isArray() || node.isObject()) {
            node.forEach(child -> collectJsonUrls(child, urls));
        }
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private boolean looksLikeImageReference(String value) {
        if (looksLikeUrl(value)) {
            return true;
        }
        String normalized = value.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.startsWith("resource/")) {
            return true;
        }
        return specs.values().stream()
                .flatMap(spec -> spec.defaultPrefixes().stream())
                .filter(prefix -> prefix != null && !prefix.isBlank())
                .anyMatch(normalized::startsWith);
    }

    private Set<String> toKeys(Collection<String> urls) {
        Set<String> keys = new LinkedHashSet<>();
        for (String url : urls) {
            String key = storageService.extractObjectKey(url);
            if (key != null && !key.isBlank()) {
                keys.add(key);
            }
        }
        return keys;
    }

    private List<String> normalizeEntities(Collection<String> entities) {
        if (entities == null || entities.isEmpty()) {
            return specs.keySet().stream().sorted().toList();
        }
        return entities.stream()
                .map(this::normalizeEntity)
                .distinct()
                .sorted()
                .toList();
    }

    private String normalizeEntity(String entity) {
        if (entity == null || entity.isBlank()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Entity is required");
        }
        String normalized = entity.trim().replace('-', '_').toUpperCase(Locale.ROOT);
        requireSpec(normalized);
        return normalized;
    }

    private EntitySpec requireSpec(String entity) {
        EntitySpec spec = specs.get(entity);
        if (spec == null) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "Unsupported image cleanup entity: " + entity + ". Supported: " + supportedEntities()
            );
        }
        return spec;
    }

    private Set<String> resolvePrefixes(Collection<String> entities, Collection<String> requestedPrefixes) {
        Set<String> prefixes = new LinkedHashSet<>();
        if (requestedPrefixes != null) {
            requestedPrefixes.stream()
                    .filter(prefix -> prefix != null && !prefix.isBlank())
                    .map(prefix -> prefix.startsWith("/") ? prefix.substring(1) : prefix)
                    .forEach(prefixes::add);
        }
        if (!prefixes.isEmpty()) {
            return prefixes;
        }
        entities.stream()
                .map(this::requireSpec)
                .flatMap(spec -> spec.defaultPrefixes().stream())
                .forEach(prefixes::add);
        if (prefixes.isEmpty()) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "No S3 prefixes resolved. Provide prefixes for the selected entities."
            );
        }
        return prefixes;
    }

    private String resolveBackupPrefix(String requestedBackupPrefix) {
        if (requestedBackupPrefix != null && !requestedBackupPrefix.isBlank()) {
            String cleaned = requestedBackupPrefix.trim();
            while (cleaned.startsWith("/")) {
                cleaned = cleaned.substring(1);
            }
            return cleaned.endsWith("/") ? cleaned : cleaned + "/";
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return "backups/image-cleanup/" + timestamp + "/";
    }

    private Map<String, EntitySpec> buildSpecs() {
        List<EntitySpec> list = List.of(
                spec("USER", "SELECT avatar_url FROM users WHERE deleted_at IS NULL AND avatar_url IS NOT NULL", "SELECT avatar_url FROM users WHERE id = ? AND avatar_url IS NOT NULL", "avatars/"),
                spec("TRIP", "SELECT cover_image_url FROM trips WHERE is_deleted = FALSE AND cover_image_url IS NOT NULL", "SELECT cover_image_url FROM trips WHERE id = ? AND cover_image_url IS NOT NULL"),
                spec("ACTIVITY", "SELECT photo_url FROM activities WHERE photo_url IS NOT NULL", "SELECT photo_url FROM activities WHERE id = ? AND photo_url IS NOT NULL", "activities/"),
                spec("EXPENSE", "SELECT receipt_url, photo_urls FROM expenses WHERE receipt_url IS NOT NULL OR photo_urls IS NOT NULL", "SELECT receipt_url, photo_urls FROM expenses WHERE id = ? AND (receipt_url IS NOT NULL OR photo_urls IS NOT NULL)", "expenses/"),
                spec("PLACE", "SELECT thumbnail, images FROM places WHERE thumbnail IS NOT NULL OR images IS NOT NULL", "SELECT thumbnail, images FROM places WHERE id = ? AND (thumbnail IS NOT NULL OR images IS NOT NULL)", "places/"),
                spec("PLACE_REVIEW", "SELECT profile_picture, images FROM place_reviews WHERE is_deleted = FALSE AND (profile_picture IS NOT NULL OR images IS NOT NULL)", "SELECT profile_picture, images FROM place_reviews WHERE id = ? AND (profile_picture IS NOT NULL OR images IS NOT NULL)", "reviews/"),
                spec("USER_REVIEW", "SELECT photos FROM user_reviews WHERE photos IS NOT NULL", "SELECT photos FROM user_reviews WHERE id = ? AND photos IS NOT NULL"),
                spec("ACTIVITY_BOOKING", "SELECT thumbnail, images, what_to_expect, itinerary FROM activity_bookings WHERE thumbnail IS NOT NULL OR images IS NOT NULL OR what_to_expect IS NOT NULL OR itinerary IS NOT NULL", "SELECT thumbnail, images, what_to_expect, itinerary FROM activity_bookings WHERE id = ? AND (thumbnail IS NOT NULL OR images IS NOT NULL OR what_to_expect IS NOT NULL OR itinerary IS NOT NULL)", "bookings/"),
                spec("FOOD", "SELECT image_url, introduction_images, varieties, how_to_eat_steps FROM foods WHERE image_url IS NOT NULL OR introduction_images IS NOT NULL OR varieties IS NOT NULL OR how_to_eat_steps IS NOT NULL", "SELECT image_url, introduction_images, varieties, how_to_eat_steps FROM foods WHERE id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL OR varieties IS NOT NULL OR how_to_eat_steps IS NOT NULL)", "foods/"),
                spec("FOOD_CITY_SCORE", "SELECT image_url, introduction_images FROM food_city_scores WHERE image_url IS NOT NULL OR introduction_images IS NOT NULL", "SELECT image_url, introduction_images FROM food_city_scores WHERE id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL)", "foods/"),
                spec("LOCATION_IMAGE", "SELECT image_url, avatar_url FROM location_images WHERE image_url IS NOT NULL OR avatar_url IS NOT NULL", "SELECT image_url, avatar_url FROM location_images WHERE id = ? AND (image_url IS NOT NULL OR avatar_url IS NOT NULL)", "location-images/"),
                spec("MEDIA_ASSET", "SELECT url FROM media_assets WHERE deleted_at IS NULL AND url IS NOT NULL", "SELECT url FROM media_assets WHERE id = ? AND url IS NOT NULL"),
                spec("SAVED_PLACE", "SELECT photo_url FROM saved_places WHERE photo_url IS NOT NULL", "SELECT photo_url FROM saved_places WHERE id = ? AND photo_url IS NOT NULL"),
                spec("CHECKIN_PHOTO", "SELECT photo_url FROM checkin_photos WHERE photo_url IS NOT NULL", "SELECT photo_url FROM checkin_photos WHERE id = ? AND photo_url IS NOT NULL"),
                spec("TRIP_PHOTO", "SELECT photo_url, thumbnail_url FROM trip_photos WHERE photo_url IS NOT NULL OR thumbnail_url IS NOT NULL", "SELECT photo_url, thumbnail_url FROM trip_photos WHERE id = ? AND (photo_url IS NOT NULL OR thumbnail_url IS NOT NULL)"),
                spec("CITY_STORY", "SELECT image_url FROM city_stories WHERE deleted_at IS NULL AND image_url IS NOT NULL", "SELECT image_url FROM city_stories WHERE id = ? AND image_url IS NOT NULL", "city-stories/")
        );

        Map<String, EntitySpec> map = new LinkedHashMap<>();
        list.stream()
                .sorted(Comparator.comparing(EntitySpec::entity))
                .forEach(spec -> map.put(spec.entity(), spec));
        return Map.copyOf(map);
    }

    private EntitySpec spec(String entity, String allSql, String recordSql, String... defaultPrefixes) {
        return new EntitySpec(entity, allSql, recordSql, List.of(defaultPrefixes));
    }

    private record EntitySpec(String entity, String allSql, String recordSql, List<String> defaultPrefixes) {
    }

    public record DeleteRecordImagesResult(
            String entity,
            UUID id,
            int referencedUrlCount,
            int deletedObjectCount,
            List<String> deletedKeys
    ) {
    }

    public record OrphanImageCleanupResult(
            List<String> entities,
            List<String> prefixes,
            boolean dryRun,
            int scannedObjectCount,
            int referencedObjectCount,
            int orphanObjectCount,
            int totalOrphanObjectCount,
            boolean limited,
            int deletedObjectCount,
            int backupObjectCount,
            String backupPrefix,
            List<String> orphanKeys
    ) {
    }
}
