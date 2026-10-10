package com.ds.goroute.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.ImageCleanupMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.time.Duration;
import java.time.Instant;
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
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImageStorageCleanupService {

    private final ImageCleanupMapper imageCleanupMapper;
    private final ObjectMapper objectMapper;
    private final StorageService storageService;

    private final Map<String, EntitySpec> specs = buildSpecs();

    /**
     * How long an object nothing points at is left alone before the sweep may delete it.
     *
     * <p>Unreferenced is not the same as unwanted while an upload is still on its way to
     * becoming a record: every composer stores the file first and saves the row that names
     * it afterwards. The longest version of that gap is a check-in captured with no
     * connection, whose photos are uploaded on the first signal and stay in the app's
     * offline queue until the draft is sent or expires (14 days). Deleting inside that
     * window takes the photos off a post that has not arrived yet, and no backup prefix
     * puts them back on it.
     */
    private static final Duration MIN_ORPHAN_AGE = Duration.ofDays(14);

    /** Every text, JSON and text-array column of the application schema. */
    private static final String TEXT_COLUMNS_SQL = """
            SELECT c.table_name, c.column_name
            FROM information_schema.columns c
            JOIN information_schema.tables t
              ON t.table_schema = c.table_schema AND t.table_name = c.table_name
            WHERE c.table_schema = current_schema()
              AND t.table_type = 'BASE TABLE'
              AND t.table_name <> 'flyway_schema_history'
              AND (c.data_type IN ('text', 'character varying', 'json', 'jsonb')
                   OR c.udt_name IN ('_text', '_varchar'))
            ORDER BY c.table_name, c.column_name
            """;

    /**
     * Columns the column scan skips because their spec reads them with a soft-delete filter on
     * purpose: deleting the row is what hands its file to the sweep. Every other column counts
     * as soon as any row names a key, deleted or not.
     */
    private static final Set<String> SPEC_FILTERED_COLUMNS = Set.of(
            "users.avatar_url",
            "trips.cover_image_url",
            "media_assets.url",
            "city_stories.image_url",
            "city_stories.video_url",
            "city_stories.thumbnail_url");

    /**
     * The only entities whose files go with the record, because nothing copies them: a user's
     * avatar (account deletion must not leave it public), a scraped Google review's photos,
     * and AR library files, which other rows reach by asset id rather than by URL.
     */
    private static final Set<String> DELETED_WITH_RECORD = Set.of("USER", "PLACE_REVIEW", "QUEST_AR_ASSET");

    /** Objects named outside the database. {@code goroute-admin/src/App.tsx} hard-codes the brand logo. */
    private static final Set<String> KEYS_USED_OUTSIDE_DATABASE = Set.of(
            "admin-media/06a38eef-3fb4-4139-9c5e-3240ea368c66-app_icon.png");

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");
    /** Prefixes go into SQL text, so only path characters are let through. */
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)*/");
    private static final String KEY_TERMINATORS = "\"'\\?#<>()[]{},;|`";

    public List<String> supportedEntities() {
        return specs.keySet().stream().sorted().toList();
    }

    /**
     * Deletes the storage objects a record owns, keeping any object that a surviving
     * record still displays -- but only for {@link #DELETED_WITH_RECORD}. For every other
     * entity this deletes nothing and leaves the files to {@link #sweepOrphans}.
     *
     * <p>Most files do not belong to one row. An AI trip activity copies the place thumbnail,
     * a cloned trip copies its activities' photos, a trip cover is a location image, a
     * check-in's photos carry the trip, activity and place it was made in, and a partner
     * Stay photo sits in three tables at once. Deleting with the record broke whichever copy
     * outlived it. The sweep deletes a file only once no row anywhere names it.
     *
     * <p>The retain step is not a nicety. A rated check-in and the review it wrote point
     * at the same uploaded files, and either one can be deleted while the other lives on;
     * without subtracting the survivor's objects, deleting one of them silently breaks
     * every photo on the other. Each entity that shares files this way declares the query
     * that finds its survivors in {@link EntitySpec#retainSql()}.
     *
     * <p>The delete itself is deferred until the surrounding transaction commits. Removing
     * an object from storage cannot be rolled back, so doing it inside the transaction
     * means a later failure leaves the row pointing at a file that is already gone.
     */
    public DeleteRecordImagesResult deleteImagesForEntityRecord(String entity, UUID id) {
        return deleteImagesForEntityRecord(entity, id, List.of());
    }

    /**
     * The same delete for a record that is being edited rather than removed: everything it
     * referenced, minus {@code keptUrls} -- the ones it still references after the edit.
     *
     * <p>Call this before the edit rewrites the rows, while the old URLs can still be read,
     * and after any record that shares them has been rewritten, so the retain step is
     * asked what survives the edit rather than what was there before it.
     */
    public DeleteRecordImagesResult deleteImagesForEntityRecord(String entity, UUID id,
                                                                Collection<String> keptUrls) {
        String normalizedEntity = normalizeEntity(entity);
        Set<String> urls = collectRecordUrls(normalizedEntity, id);
        if (!DELETED_WITH_RECORD.contains(normalizedEntity)) {
            log.debug("Left {} image refs of entity {} record {} to the orphan sweep",
                    urls.size(), normalizedEntity, id);
            return new DeleteRecordImagesResult(normalizedEntity, id, urls.size(), 0, List.of());
        }
        Set<String> keys = toKeys(urls);
        keys.removeAll(toKeys(keptUrls));

        Set<String> retainedKeys = collectRetainedKeys(normalizedEntity, id);
        if (!retainedKeys.isEmpty()) {
            int before = keys.size();
            keys.removeAll(retainedKeys);
            if (before != keys.size()) {
                log.info("Kept {} image objects still shown by another record. entity={}, record={}",
                        before - keys.size(), normalizedEntity, id);
            }
        }

        List<String> deletable = new ArrayList<>(keys);
        deleteAfterCommit(deletable, normalizedEntity, id);
        return new DeleteRecordImagesResult(normalizedEntity, id, urls.size(), deletable.size(), deletable);
    }

    /**
     * Objects another surviving record still points at, and which therefore must outlive
     * this delete. Empty for every entity that does not share files with another table.
     */
    private Set<String> collectRetainedKeys(String entity, UUID id) {
        String retainSql = requireSpec(entity).retainSql();
        if (retainSql == null || retainSql.isBlank()) {
            return Set.of();
        }
        return toKeys(queryUrls(retainSql, id));
    }

    /**
     * Runs the delete after the transaction commits, or immediately when there is no
     * transaction to wait for. A rollback then leaves storage untouched, which is the
     * recoverable direction: an object nobody references is found by the orphan sweep,
     * while a row pointing at a deleted object is a broken image with no way back.
     */
    private void deleteAfterCommit(List<String> keys, String entity, UUID id) {
        if (keys.isEmpty()) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            runDelete(keys, entity, id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                runDelete(keys, entity, id);
            }
        });
    }

    private void runDelete(List<String> keys, String entity, UUID id) {
        storageService.deleteObjectKeys(keys);
        log.info("Deleted {} image objects for entity {} record {}", keys.size(), entity, id);
    }

    /**
     * The unattended sweep of one prefix: deletes objects under it that no table references and
     * that are older than {@code minAge}, at most {@code limit} per call, with no backup copy --
     * the point is to free the space. "Unreferenced" means no database column names the key
     * (see {@link #collectReferencedKeys}).
     *
     * @return the keys deleted
     */
    public List<String> deleteOrphansUnder(String prefix, Duration minAge, int limit) {
        return deleteOrphansUnder(List.of(prefix), minAge, limit);
    }

    /**
     * The nightly sweep: every prefix the specs know, {@link #MIN_ORPHAN_AGE} old, no backup.
     * This is how files of deleted and edited records are freed for every entity outside
     * {@link #DELETED_WITH_RECORD}.
     */
    public List<String> sweepOrphans(int limit) {
        List<String> prefixes = specs.values().stream()
                .flatMap(spec -> spec.defaultPrefixes().stream())
                .distinct()
                .sorted()
                .toList();
        return deleteOrphansUnder(prefixes, MIN_ORPHAN_AGE, limit);
    }

    private List<String> deleteOrphansUnder(Collection<String> prefixes, Duration minAge, int limit) {
        Set<String> referencedKeys = collectReferencedKeys(specs.keySet(), prefixes);
        Instant newestDeletable = Instant.now().minus(minAge);
        Map<String, Instant> scanned = new LinkedHashMap<>();
        for (String prefix : prefixes) {
            for (StorageService.StoredObject object : storageService.listObjects(prefix)) {
                scanned.putIfAbsent(object.key(), object.lastModified());
            }
        }
        List<String> orphans = scanned.entrySet().stream()
                .filter(object -> !referencedKeys.contains(object.getKey()))
                // An unknown write time is treated as brand new, as in the manual sweep.
                .filter(object -> object.getValue() != null && !object.getValue().isAfter(newestDeletable))
                .map(Map.Entry::getKey)
                .sorted()
                .limit(Math.max(1, limit))
                .toList();
        if (!orphans.isEmpty()) {
            storageService.deleteObjectKeys(orphans);
            log.info("Deleted {} orphaned objects under {} (older than {} h)", orphans.size(), prefixes, minAge.toHours());
        }
        return orphans;
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

        if (!dryRun && !backupBeforeDelete) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "backupBeforeDelete=true is required when dryRun=false"
            );
        }
        Set<String> referencedKeys = collectReferencedKeys(specs.keySet(), prefixes);

        Map<String, Instant> scanned = new LinkedHashMap<>();
        for (String prefix : prefixes) {
            for (StorageService.StoredObject object : storageService.listObjects(prefix)) {
                scanned.putIfAbsent(object.key(), object.lastModified());
            }
        }

        Instant newestDeletable = Instant.now().minus(MIN_ORPHAN_AGE);
        List<String> allOrphanKeys = new ArrayList<>();
        int tooNewCount = 0;
        for (String key : scanned.keySet().stream().sorted().toList()) {
            if (referencedKeys.contains(key)) {
                continue;
            }
            Instant writtenAt = scanned.get(key);
            // An unknown write time is treated as brand new. Guessing old here is the one
            // mistake this sweep cannot take back.
            if (writtenAt == null || writtenAt.isAfter(newestDeletable)) {
                tooNewCount++;
                continue;
            }
            allOrphanKeys.add(key);
        }
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
                "Image orphan cleanup completed. dryRun={}, prefixes={}, scanned={}, referenced={}, totalOrphans={}, returnedOrphans={}, keptTooNew={}, minAgeDays={}, backedUp={}, deleted={}",
                dryRun,
                prefixes,
                scanned.size(),
                referencedKeys.size(),
                allOrphanKeys.size(),
                orphanKeys.size(),
                tooNewCount,
                MIN_ORPHAN_AGE.toDays(),
                backupCount,
                deletedCount
        );

        return new OrphanImageCleanupResult(
                normalizedEntities,
                new ArrayList<>(prefixes),
                dryRun,
                scanned.size(),
                referencedKeys.size(),
                orphanKeys.size(),
                allOrphanKeys.size(),
                allOrphanKeys.size() > orphanKeys.size(),
                tooNewCount,
                MIN_ORPHAN_AGE.toDays(),
                deletedCount,
                backupCount,
                resolvedBackupPrefix,
                orphanKeys
        );
    }

    /**
     * Every key under {@code prefixes} that something in the database still names: the spec
     * queries, plus a scan of every text and JSON column in the schema.
     *
     * <p>The specs alone are a hand-kept list, and a list like that falls behind the schema:
     * {@code places.menu}, partner listing snapshots and AI trip drafts all held live
     * {@code places/} URLs the specs never read, so the sweep would have deleted them. The
     * column scan cannot fall behind. It errs towards keeping: any text that contains a key
     * keeps that key, whatever column or JSON shape it sits in.
     */
    private Set<String> collectReferencedKeys(Collection<String> entities, Collection<String> prefixes) {
        Set<String> urls = new LinkedHashSet<>();
        for (String entity : entities) {
            EntitySpec spec = requireSpec(entity);
            queryRows(spec.allSql()).stream()
                    .filter(row -> row != null && !row.isEmpty())
                    .forEach(row -> urls.addAll(extractUrls(row.values())));
        }
        Set<String> keys = toKeys(urls);
        keys.addAll(collectKeysNamedAnywhere(prefixes));
        keys.addAll(KEYS_USED_OUTSIDE_DATABASE);
        return keys;
    }

    private Set<String> collectKeysNamedAnywhere(Collection<String> prefixes) {
        List<String> safePrefixes = prefixes.stream().map(this::requireSafePrefix).distinct().toList();
        Set<String> keys = new LinkedHashSet<>();
        if (safePrefixes.isEmpty()) {
            return keys;
        }
        String likeAny = safePrefixes.stream()
                .map(prefix -> "%" + prefix + "%")
                .map(pattern -> "'" + pattern + "'")
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow();
        for (Map<String, Object> column : queryRows(TEXT_COLUMNS_SQL)) {
            String table = String.valueOf(column.get("table_name"));
            String name = String.valueOf(column.get("column_name"));
            if (!SQL_IDENTIFIER.matcher(table).matches() || !SQL_IDENTIFIER.matcher(name).matches()) {
                // Skipping a column could delete what it names, so stop instead.
                throw new BusinessException(ErrorConstant.INTERNAL_SERVER_ERROR,
                        "Unexpected column name " + table + "." + name + "; cleanup aborted");
            }
            if (SPEC_FILTERED_COLUMNS.contains(table + "." + name)) {
                continue;
            }
            String sql = "SELECT \"" + name + "\"::text AS ref FROM \"" + table + "\""
                    + " WHERE \"" + name + "\"::text LIKE ANY (ARRAY[" + likeAny + "])";
            for (Map<String, Object> row : queryRows(sql)) {
                Object ref = row == null ? null : row.get("ref");
                if (ref != null) {
                    collectKeysInText(ref.toString(), safePrefixes, keys);
                }
            }
        }
        return keys;
    }

    /**
     * Cuts each key out of free text: from a prefix that starts a path segment to the first
     * character a key never holds. A URL, a bare key and a key inside JSON all yield the same key.
     */
    private static void collectKeysInText(String text, List<String> prefixes, Set<String> keys) {
        for (String prefix : prefixes) {
            int from = text.indexOf(prefix);
            while (from >= 0) {
                boolean startsSegment = from == 0 || !Character.isLetterOrDigit(text.charAt(from - 1));
                int end = from + prefix.length();
                while (end < text.length() && KEY_TERMINATORS.indexOf(text.charAt(end)) < 0
                        && !Character.isWhitespace(text.charAt(end))) {
                    end++;
                }
                if (startsSegment && end > from + prefix.length()) {
                    String key = text.substring(from, end);
                    keys.add(key);
                    // Older keys carry the original file name, which a stored URL may percent-encode.
                    if (key.indexOf('%') >= 0) {
                        try {
                            keys.add(URLDecoder.decode(key, StandardCharsets.UTF_8));
                        } catch (IllegalArgumentException ignored) {
                            // Not valid percent-encoding: the raw key is all there is.
                        }
                    }
                }
                from = text.indexOf(prefix, from + prefix.length());
            }
        }
    }

    private String requireSafePrefix(String prefix) {
        if (prefix == null || !SAFE_PREFIX.matcher(prefix).matches()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Invalid storage prefix: " + prefix);
        }
        return prefix;
    }

    private Set<String> collectRecordUrls(String entity, UUID id) {
        Set<String> urls = new LinkedHashSet<>();

        switch (entity) {
            case "TRIP" -> {
                // trips.cover_image_url is left out on purpose: a cloned trip copies the same URL,
                // so deleting the original must not remove the file the clone still shows. The
                // orphan sweep reclaims it once no trip references it (see the TRIP allSql).
                urls.addAll(queryUrls("SELECT photo_url FROM activities WHERE trip_id = ? AND photo_url IS NOT NULL", id));
                urls.addAll(queryUrls("SELECT url FROM media_assets WHERE trip_id = ? AND deleted_at IS NULL AND url IS NOT NULL", id));
            }
            case "PLACE" -> {
                urls.addAll(queryUrls("SELECT thumbnail, images, menu FROM places WHERE id = ? AND (thumbnail IS NOT NULL OR images IS NOT NULL OR menu IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT profile_picture, images FROM place_reviews WHERE place_id = ? AND (profile_picture IS NOT NULL OR images IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT url FROM media_assets WHERE place_id = ? AND entity_type = 'USER_REVIEW' AND deleted_at IS NULL AND url IS NOT NULL", id));
            }
            case "FOOD" -> {
                urls.addAll(queryUrls("SELECT image_url, introduction_images, varieties, how_to_eat_steps FROM foods WHERE id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL OR varieties IS NOT NULL OR how_to_eat_steps IS NOT NULL)", id));
                urls.addAll(queryUrls("SELECT image_url, introduction_images FROM food_city_scores WHERE food_id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL)", id));
            }
            case "ACTIVITY" -> {
                urls.addAll(queryUrls("SELECT photo_url FROM activities WHERE id = ? AND photo_url IS NOT NULL", id));
                urls.addAll(queryUrls("SELECT url FROM media_assets WHERE activity_id = ? AND deleted_at IS NULL AND url IS NOT NULL", id));
                urls.addAll(queryUrls("""
                        SELECT cp.photo_url
                        FROM checkin_photos cp
                        JOIN checkins c ON c.id = cp.checkin_id
                        WHERE c.activity_id = ?
                          AND cp.photo_url IS NOT NULL
                        """, id));
            }
            default -> urls.addAll(queryUrls(requireRecordSql(entity), id));
        }

        return urls;
    }

    private String requireRecordSql(String entity) {
        String recordSql = requireSpec(entity).recordSql();
        if (recordSql == null) {
            throw new BusinessException(
                    ErrorConstant.INVALID_PARAMETERS,
                    "Entity " + entity + " has no single-id record key; use the orphan cleanup instead"
            );
        }
        return recordSql;
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
            log.error("Image reference query failed. Cleanup stopped to avoid unsafe deletes: {}", e.getMessage(), e);
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
                    .map(prefix -> prefix.endsWith("/") ? prefix : prefix + "/")
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
                spec("EXPENSE", "SELECT url FROM media_assets WHERE entity_type = 'EXPENSE' AND deleted_at IS NULL AND url IS NOT NULL", "SELECT url FROM media_assets WHERE entity_type = 'EXPENSE' AND entity_id = ? AND deleted_at IS NULL AND url IS NOT NULL", "expenses/"),
                spec("PLACE", "SELECT thumbnail, images, menu FROM places WHERE thumbnail IS NOT NULL OR images IS NOT NULL OR menu IS NOT NULL", "SELECT thumbnail, images, menu FROM places WHERE id = ? AND (thumbnail IS NOT NULL OR images IS NOT NULL OR menu IS NOT NULL)", "places/"),
                spec("PLACE_REVIEW", "SELECT profile_picture, images FROM place_reviews WHERE is_deleted = FALSE AND (profile_picture IS NOT NULL OR images IS NOT NULL)", "SELECT profile_picture, images FROM place_reviews WHERE id = ? AND (profile_picture IS NOT NULL OR images IS NOT NULL)", "reviews/"),
                // A review written from a check-in shows that visit's uploads. Check-ins
                // outlive the review they wrote, so deleting the review must leave the
                // files of every check-in still pointing at it.
                retaining("USER_REVIEW",
                        "SELECT url FROM media_assets WHERE entity_type = 'USER_REVIEW' AND deleted_at IS NULL AND url IS NOT NULL",
                        "SELECT url FROM media_assets WHERE entity_type = 'USER_REVIEW' AND entity_id = ? AND deleted_at IS NULL AND url IS NOT NULL",
                        """
                        SELECT m.url
                        FROM media_assets m
                        JOIN user_checkins c ON c.id = m.entity_id
                        WHERE m.entity_type = 'USER_CHECKIN'
                          AND m.deleted_at IS NULL
                          AND c.review_id = ?
                          AND c.is_removed = FALSE
                          AND m.url IS NOT NULL
                        """),
                spec("ACTIVITY_BOOKING", "SELECT thumbnail, images, what_to_expect, itinerary FROM activity_bookings WHERE thumbnail IS NOT NULL OR images IS NOT NULL OR what_to_expect IS NOT NULL OR itinerary IS NOT NULL", "SELECT thumbnail, images, what_to_expect, itinerary FROM activity_bookings WHERE id = ? AND (thumbnail IS NOT NULL OR images IS NOT NULL OR what_to_expect IS NOT NULL OR itinerary IS NOT NULL)", "bookings/"),
                spec("FOOD", "SELECT image_url, introduction_images, varieties, how_to_eat_steps FROM foods WHERE image_url IS NOT NULL OR introduction_images IS NOT NULL OR varieties IS NOT NULL OR how_to_eat_steps IS NOT NULL", "SELECT image_url, introduction_images, varieties, how_to_eat_steps FROM foods WHERE id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL OR varieties IS NOT NULL OR how_to_eat_steps IS NOT NULL)", "foods/"),
                spec("FOOD_CITY_SCORE", "SELECT image_url, introduction_images FROM food_city_scores WHERE image_url IS NOT NULL OR introduction_images IS NOT NULL", "SELECT image_url, introduction_images FROM food_city_scores WHERE id = ? AND (image_url IS NOT NULL OR introduction_images IS NOT NULL)", "foods/"),
                spec("LOCATION_IMAGE", "SELECT image_url, avatar_url FROM location_images WHERE image_url IS NOT NULL OR avatar_url IS NOT NULL", "SELECT image_url, avatar_url FROM location_images WHERE id = ? AND (image_url IS NOT NULL OR avatar_url IS NOT NULL)", "location-images/"),
                // Admin media is soft-deleted, so allSql filtering on deleted_at is what
                // releases the object to the orphan sweep. That only closes the loop if the
                // prefix admin uploads write to is one the sweep actually scans.
                spec("MEDIA_ASSET", "SELECT url FROM media_assets WHERE deleted_at IS NULL AND url IS NOT NULL", "SELECT url FROM media_assets WHERE id = ? AND url IS NOT NULL", "trip-memory-videos/", "admin-media/"),
                spec("SAVED_PLACE", "SELECT photo_url FROM saved_places WHERE photo_url IS NOT NULL", "SELECT photo_url FROM saved_places WHERE id = ? AND photo_url IS NOT NULL"),
                spec("CHECKIN_PHOTO", "SELECT photo_url FROM checkin_photos WHERE photo_url IS NOT NULL", "SELECT photo_url FROM checkin_photos WHERE id = ? AND photo_url IS NOT NULL"),
                spec("TRIP_PHOTO", "SELECT photo_url, thumbnail_url FROM trip_photos WHERE photo_url IS NOT NULL OR thumbnail_url IS NOT NULL", "SELECT photo_url, thumbnail_url FROM trip_photos WHERE id = ? AND (photo_url IS NOT NULL OR thumbnail_url IS NOT NULL)"),
                spec("CITY_STORY", "SELECT image_url, video_url, thumbnail_url FROM city_stories WHERE deleted_at IS NULL AND (image_url IS NOT NULL OR video_url IS NOT NULL OR thumbnail_url IS NOT NULL)", "SELECT image_url, video_url, thumbnail_url FROM city_stories WHERE id = ? AND (image_url IS NOT NULL OR video_url IS NOT NULL OR thumbnail_url IS NOT NULL)", "city-stories/"),

                // Everything below shares the generic upload door (/v1/api/files/upload),
                // which writes under expenses/<userId>/. That prefix is scanned, so a table
                // missing from this list does not merely go uncleaned: its objects read as
                // orphans and the sweep deletes them.
                //
                // The record query is keyed by the check-in, not by the photo row, because
                // the caller deleting a check-in holds the check-in id.
                retaining("USER_CHECKIN_PHOTO",
                        "SELECT url FROM media_assets WHERE entity_type = 'USER_CHECKIN' AND deleted_at IS NULL AND url IS NOT NULL",
                        "SELECT url FROM media_assets WHERE entity_type = 'USER_CHECKIN' AND entity_id = ? AND deleted_at IS NULL AND url IS NOT NULL",
                        // The review this visit wrote keeps showing the same files.
                        """
                        SELECT m.url
                        FROM media_assets m
                        JOIN user_checkins c ON c.review_id = m.entity_id
                        WHERE m.entity_type = 'USER_REVIEW'
                          AND m.deleted_at IS NULL
                          AND c.id = ?
                          AND m.url IS NOT NULL
                        """),
                spec("PENDING_CONTRIBUTION_REVIEW",
                        "SELECT url FROM media_assets WHERE entity_type = 'PENDING_REVIEW' AND deleted_at IS NULL AND url IS NOT NULL",
                        "SELECT m.url FROM media_assets m JOIN pending_contribution_reviews p ON p.id = m.entity_id WHERE p.contribution_id = ? AND m.entity_type = 'PENDING_REVIEW' AND m.deleted_at IS NULL AND m.url IS NOT NULL"),
                spec("PLACE_COLLECTION",
                        "SELECT cover_image_url FROM place_collections WHERE cover_image_url IS NOT NULL",
                        "SELECT cover_image_url FROM place_collections WHERE id = ? AND cover_image_url IS NOT NULL"),
                spec("SAVED_CATEGORY",
                        "SELECT cover_url FROM saved_categories WHERE cover_url IS NOT NULL",
                        "SELECT cover_url FROM saved_categories WHERE id = ? AND cover_url IS NOT NULL"),
                spec("PASSPORT_DEFINITION",
                        "SELECT cover_image_url FROM passport_definitions WHERE cover_image_url IS NOT NULL",
                        "SELECT cover_image_url FROM passport_definitions WHERE id = ? AND cover_image_url IS NOT NULL",
                        "passport-catalog/"),
                spec("PASSPORT_TAG",
                        "SELECT image_url FROM passport_tags WHERE image_url IS NOT NULL",
                        "SELECT image_url FROM passport_tags WHERE id = ? AND image_url IS NOT NULL",
                        "passport-catalog/"),
                // icon started as an emoji and now holds an uploaded badge URL (V134).
                // Keyed by (code, version), so there is no single UUID to address one rule
                // by: it takes part in the orphan sweep but not in per-record deletes.
                spec("PASSPORT_STAMP_RULE",
                        "SELECT icon FROM passport_stamp_rules WHERE icon IS NOT NULL",
                        null,
                        "passport-catalog/"),
                // Quest media (§3.13–3.15). Photos come through the generic upload door
                // (expenses/<userId>/), voice clips through quest-audio/. Every version is
                // kept -- a run in progress may still be playing an old one -- so every
                // version's rows count. Checkpoints carry no single-UUID owner of their own
                // media, so quests take part in the orphan sweep only.
                spec("QUEST",
                        """
                        SELECT cover_url AS ref FROM quest_versions WHERE cover_url IS NOT NULL
                        UNION ALL SELECT image_urls::text FROM quest_checkpoints WHERE image_urls <> '[]'::jsonb
                        UNION ALL SELECT story_audio_url FROM quest_checkpoints WHERE story_audio_url IS NOT NULL
                        UNION ALL SELECT ar_object::text FROM quest_checkpoints WHERE ar_object IS NOT NULL
                        UNION ALL SELECT image_url FROM quest_checkpoint_clues WHERE image_url IS NOT NULL
                        UNION ALL SELECT image_urls::text FROM quest_checkpoint_stops WHERE image_urls <> '[]'::jsonb
                        UNION ALL SELECT audio_url FROM quest_checkpoint_stops WHERE audio_url IS NOT NULL
                        """,
                        null,
                        "quest-audio/"),
                // The AR object library's models and thumbnails. Two rows may name the same file
                // (an object duplicated to try a new thumbnail), so a replaced or deleted file
                // is kept while another row still uses it.
                retaining("QUEST_AR_ASSET",
                        "SELECT glb_url, usdz_url, thumbnail_url FROM quest_ar_object_assets",
                        "SELECT glb_url, usdz_url, thumbnail_url FROM quest_ar_object_assets WHERE id = ?",
                        "SELECT glb_url, usdz_url, thumbnail_url FROM quest_ar_object_assets WHERE id <> ?",
                        "quest-ar/"),
                // Local events (epic 17): the cover and every re-hosted image. Crawled images are
                // stored under events/<eventId>/, console uploads under events/uploads/.
                spec("LOCAL_EVENT",
                        """
                        SELECT cover_url AS ref FROM local_events WHERE cover_url IS NOT NULL
                        UNION ALL SELECT url FROM local_event_images WHERE url IS NOT NULL
                        """,
                        "SELECT e.cover_url, i.url FROM local_events e LEFT JOIN local_event_images i ON i.event_id = e.id WHERE e.id = ?",
                        "events/"),
                spec("ROOM_TYPE",
                        "SELECT images FROM room_types WHERE images IS NOT NULL",
                        "SELECT images FROM room_types WHERE id = ? AND images IS NOT NULL",
                        "marketplace/hotels/", "marketplace/activities/")
        );

        Map<String, EntitySpec> map = new LinkedHashMap<>();
        list.stream()
                .sorted(Comparator.comparing(EntitySpec::entity))
                .forEach(spec -> map.put(spec.entity(), spec));
        return Map.copyOf(map);
    }

    private EntitySpec spec(String entity, String allSql, String recordSql, String... defaultPrefixes) {
        return new EntitySpec(entity, allSql, recordSql, null, List.of(defaultPrefixes));
    }

    /** An entity whose files are also displayed by another record that can outlive it. */
    private EntitySpec retaining(String entity, String allSql, String recordSql, String retainSql,
                                 String... defaultPrefixes) {
        return new EntitySpec(entity, allSql, recordSql, retainSql, List.of(defaultPrefixes));
    }

    /**
     * @param allSql         every URL this table currently references; drives the orphan sweep
     * @param recordSql      URLs owned by one record, or {@code null} when the table has no
     *                       single-UUID key to address a record by
     * @param retainSql      URLs a surviving record still displays, keyed the same way as
     *                       {@code recordSql}; {@code null} when this table shares nothing
     */
    private record EntitySpec(String entity, String allSql, String recordSql, String retainSql,
                              List<String> defaultPrefixes) {
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
            int keptTooNewObjectCount,
            long minimumOrphanAgeDays,
            int deletedObjectCount,
            int backupObjectCount,
            String backupPrefix,
            List<String> orphanKeys
    ) {
    }
}
