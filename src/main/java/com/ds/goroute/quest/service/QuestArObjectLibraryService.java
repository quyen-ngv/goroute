package com.ds.goroute.quest.service;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.quest.domain.QuestArObjectAsset;
import com.ds.goroute.quest.dto.QuestArObjectAssetResponse;
import com.ds.goroute.quest.dto.QuestArObjectFileResponse;
import com.ds.goroute.quest.dto.QuestArObjectView;
import com.ds.goroute.quest.dto.SaveQuestArObjectAssetRequest;
import com.ds.goroute.quest.persistence.QuestRepository;
import com.ds.goroute.service.ImageStorageCleanupService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.marketplace.MarketplaceJson;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The AR object library (§3.15): the console uploads and edits 3D objects here, creators only list
 * them. Every GLB is inspected against the asset spec before it is stored, and again when an
 * object is saved, so the clips and triangle count on the row always describe the file it points
 * to.
 */
@Service
@RequiredArgsConstructor
public class QuestArObjectLibraryService {

    /** Above the spec's 5 MB target, which only warns; this is the hard ceiling. */
    static final long MAX_MODEL_BYTES = 10L * 1024 * 1024;
    static final long MAX_THUMBNAIL_BYTES = 1024L * 1024;

    private final QuestRepository repository;
    private final StorageService storage;
    private final MarketplaceJson json;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ImageStorageCleanupService cleanup;

    /** This library's entry in {@link ImageStorageCleanupService}. */
    static final String STORAGE_ENTITY = "QUEST_AR_ASSET";

    public enum FileKind { GLB, USDZ, THUMBNAIL }

    @Transactional(readOnly = true)
    public List<QuestArObjectAssetResponse> list(boolean activeOnly) {
        return repository.findArObjectAssets(activeOnly).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public QuestArObjectAssetResponse get(UUID id) {
        return view(require(id));
    }

    /** Checks and stores one file. A GLB that breaks the spec is not stored: the reply lists why. */
    public QuestArObjectFileResponse upload(UUID adminUserId, FileKind kind, MultipartFile file) {
        byte[] bytes = read(file, kind == FileKind.THUMBNAIL ? MAX_THUMBNAIL_BYTES : MAX_MODEL_BYTES);
        String prefix = "quest-ar/" + UUID.randomUUID();
        return switch (kind) {
            case GLB -> {
                ArModelInspector.Inspection inspection = ArModelInspector.inspectGlb(bytes, objectMapper);
                String url = inspection.usable()
                        ? store(prefix + ".glb", bytes, "model/gltf-binary")
                        : null;
                yield new QuestArObjectFileResponse(kind.name(), url, bytes.length, inspection.triangles(),
                        inspection.joints(), clips(inspection.clips()), inspection.canWander(),
                        inspection.errors(), inspection.warnings());
            }
            case USDZ -> {
                if (!ArModelInspector.isUsdz(bytes)) {
                    yield new QuestArObjectFileResponse(kind.name(), null, bytes.length, null, null, null, null,
                            List.of("The file is not a USDZ (a zip whose first file is a USD layer)"), List.of());
                }
                yield new QuestArObjectFileResponse(kind.name(),
                        store(prefix + ".usdz", bytes, "model/vnd.usdz+zip"), bytes.length, null, null, null, null,
                        List.of(), bytes.length > ArModelInspector.WARN_BYTES
                                ? List.of("Larger than 5 MB; players download it with the quest") : List.of());
            }
            case THUMBNAIL -> {
                String type = imageType(bytes);
                if (type == null) {
                    yield new QuestArObjectFileResponse(kind.name(), null, bytes.length, null, null, null, null,
                            List.of("A thumbnail must be PNG, JPEG or WEBP"), List.of());
                }
                yield new QuestArObjectFileResponse(kind.name(),
                        store(prefix + "." + type.substring(type.indexOf('/') + 1), bytes, type), bytes.length,
                        null, null, null, null, List.of(), List.of());
            }
        };
    }

    @Transactional
    public QuestArObjectAssetResponse create(UUID adminUserId, SaveQuestArObjectAssetRequest request) {
        LocalDateTime now = LocalDateTime.now();
        QuestArObjectAsset asset = QuestArObjectAsset.builder()
                .id(UUID.randomUUID())
                .createdBy(adminUserId)
                .createdAt(now)
                .build();
        apply(asset, request, now);
        repository.insertArObjectAsset(asset);
        return get(asset.getId());
    }

    @Transactional
    public QuestArObjectAssetResponse update(UUID id, SaveQuestArObjectAssetRequest request) {
        QuestArObjectAsset asset = require(id);
        long expected = request.expectedVersion() != null && request.expectedVersion() > 0
                ? request.expectedVersion() : asset.getDataVersion();
        apply(asset, request, LocalDateTime.now());
        // Files the edit replaced or cleared leave storage once it commits (models run to
        // megabytes each). The cleanup reads the old row, so it runs before the update rewrites it.
        List<String> kept = Stream.of(asset.getGlbUrl(), asset.getUsdzUrl(), asset.getThumbnailUrl())
                .filter(Objects::nonNull).toList();
        cleanup.deleteImagesForEntityRecord(STORAGE_ENTITY, id, kept);
        if (!repository.updateArObjectAsset(asset, expected)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This AR object was changed somewhere else. Reload it and try again.");
        }
        return get(id);
    }

    /**
     * Removes an object from the library and its files from storage. An object placed on any
     * checkpoint, of any version, stays: published quests and runs in progress still show it.
     * Hiding it ({@code active = false}) takes it off the creators' list instead.
     */
    @Transactional
    public void delete(UUID id, Long expectedVersion) {
        QuestArObjectAsset asset = require(id);
        int uses = repository.countCheckpointsUsingArObjectAsset(id);
        if (uses > 0) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "This AR object is placed on " + uses + " checkpoint" + (uses == 1 ? "" : "s")
                            + ". Hide it instead, so no new quest can pick it.");
        }
        long expected = expectedVersion != null && expectedVersion > 0 ? expectedVersion : asset.getDataVersion();
        // Reads the row's files before it goes; the objects themselves are deleted after commit.
        cleanup.deleteImagesForEntityRecord(STORAGE_ENTITY, id);
        if (!repository.deleteArObjectAsset(id, expected)) {
            throw new BusinessException(ErrorConstant.ALREADY_PROCESSED,
                    "This AR object was changed somewhere else. Reload it and try again.");
        }
    }

    /**
     * Copies the request onto the row and re-reads the GLB it names, so clips, triangles and
     * can-wander describe that exact file. A GLB that no longer passes the spec is refused.
     */
    private void apply(QuestArObjectAsset asset, SaveQuestArObjectAssetRequest request, LocalDateTime now) {
        String glbUrl = httpUrl(request.glbUrl(), "GLB");
        if (!glbUrl.equals(asset.getGlbUrl()) || asset.getClips() == null) {
            byte[] glb = download(glbUrl);
            ArModelInspector.Inspection inspection = ArModelInspector.inspectGlb(glb, objectMapper);
            if (!inspection.usable()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "The GLB does not meet the AR asset spec: " + String.join("; ", inspection.errors()));
            }
            asset.setGlbBytes((long) glb.length);
            asset.setTriangles(inspection.triangles());
            asset.setCanWander(inspection.canWander());
            asset.setClips(json.write(clips(inspection.clips())));
        }
        asset.setGlbUrl(glbUrl);
        String usdz = blankToNull(request.usdzUrl());
        if (usdz != null && !usdz.equals(asset.getUsdzUrl())) {
            asset.setUsdzBytes((long) download(httpUrl(usdz, "USDZ")).length);
        } else if (usdz == null) {
            asset.setUsdzBytes(null);
        }
        asset.setUsdzUrl(usdz);
        String thumbnail = blankToNull(request.thumbnailUrl());
        asset.setThumbnailUrl(thumbnail == null ? null : httpUrl(thumbnail, "thumbnail"));
        asset.setName(request.name().trim());
        asset.setDescription(blankToNull(request.description()));
        asset.setHeightM(request.heightM());
        asset.setTags(json.write(request.tags() == null ? List.of() : request.tags().stream()
                .filter(t -> t != null && !t.isBlank()).map(t -> t.trim().toLowerCase(Locale.ROOT)).distinct()
                .toList()));
        asset.setActive(request.active() == null || request.active());
        asset.setUpdatedAt(now);
    }

    private QuestArObjectAssetResponse view(QuestArObjectAsset a) {
        List<QuestArObjectView.Clip> clips = json.readMaps(a.getClips()).stream()
                .map(clip -> new QuestArObjectView.Clip(String.valueOf(clip.get("name")),
                        clip.get("seconds") instanceof Number n ? n.doubleValue() : 0))
                .toList();
        return new QuestArObjectAssetResponse(a.getId(), a.getName(), a.getDescription(), a.getGlbUrl(),
                a.getGlbBytes(), a.getUsdzUrl(), a.getUsdzBytes(), a.getThumbnailUrl(), a.getHeightM(), clips,
                a.getTriangles(), a.isCanWander(), json.readList(a.getTags(), String.class), a.isActive(),
                a.getDataVersion() == null ? 0 : a.getDataVersion(), a.getUpdatedAt());
    }

    private static List<QuestArObjectView.Clip> clips(List<ArModelInspector.Clip> clips) {
        return clips.stream().map(c -> new QuestArObjectView.Clip(c.name(), c.seconds())).toList();
    }

    private QuestArObjectAsset require(UUID id) {
        return repository.findArObjectAsset(id)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "AR object not found"));
    }

    private String store(String key, byte[] bytes, String contentType) {
        return storage.uploadFile(key, new ByteArrayInputStream(bytes), contentType, bytes.length);
    }

    private byte[] download(String url) {
        byte[] bytes;
        try {
            bytes = restTemplate.getForObject(url, byte[].class);
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Could not download " + url);
        }
        if (bytes == null || bytes.length == 0) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Nothing was found at " + url);
        }
        if (bytes.length > MAX_MODEL_BYTES) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The file at " + url + " is over 10 MB");
        }
        return bytes;
    }

    private static byte[] read(MultipartFile file, long max) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The file is empty");
        }
        if (file.getSize() > max) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "The file is larger than " + (max / 1024 / 1024 == 0 ? max / 1024 + " KB" : max / 1024 / 1024 + " MB"));
        }
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the uploaded file", ex);
        }
    }

    static String imageType(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static String httpUrl(String raw, String what) {
        String url = raw == null ? "" : raw.trim();
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The " + what + " link is not a URL");
        }
        return url;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
