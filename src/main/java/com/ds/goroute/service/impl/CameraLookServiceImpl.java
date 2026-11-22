package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.CreateCameraLookRequest;
import com.ds.goroute.dto.request.ReorderCameraLooksRequest;
import com.ds.goroute.dto.request.UpdateCameraLookRequest;
import com.ds.goroute.dto.response.CameraLookManifestResponse;
import com.ds.goroute.dto.response.CameraLookResponse;
import com.ds.goroute.dto.response.CameraLookUploadResponse;
import com.ds.goroute.entity.CameraLook;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.CameraLookRepository;
import com.ds.goroute.repository.UserCheckinRepository;
import com.ds.goroute.service.CameraLookService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.service.external.FirebaseService;
import com.ds.goroute.type.CameraLookKind;
import com.ds.goroute.type.CameraLookStatus;
import com.ds.goroute.utils.CameraLookDefinitionValidator;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CameraLookServiceImpl implements CameraLookService {

    /**
     * Storage prefix of LUT, frame and thumbnail PNGs. It is deliberately not an
     * {@code ImageCompressionGroup}: the bulk run and the on-upload compressor both skip keys
     * outside every group, and a re-encoded LUT would shift every colour of every photo.
     * The orphan sweep frees replaced files (ImageStorageCleanupService, CAMERA_LOOK).
     */
    static final String STORAGE_PREFIX = "camera-looks";

    /**
     * The FCM topic every signed-in app subscribes to, and the {@code type} of the data message
     * sent on it. Both are read by the app (push_notification_service.dart); change them together.
     */
    static final String CHANGED_TOPIC = "camera_looks";
    static final String CHANGED_TYPE = "CAMERA_LOOKS_CHANGED";

    /** The catalogue is curated by hand; this only keeps the reads bounded. */
    private static final int MAX_LOOKS = 500;

    /** The app's languages; a name in any other tag is refused. */
    private static final List<String> NAME_LANGUAGES = List.of(
            "en", "vi", "de", "es", "fr", "hi", "ja", "ko", "ru", "th", "zh-CN", "zh-TW");
    private static final String REQUIRED_NAME_LANGUAGE = "en";
    private static final int MAX_NAME_LENGTH = 100;

    private final CameraLookRepository cameraLookRepository;
    private final UserCheckinRepository userCheckinRepository;
    private final FileUploadService fileUploadService;
    private final StorageService storageService;
    private final FirebaseService firebaseService;

    @Override
    @Transactional(readOnly = true)
    public List<CameraLookResponse> adminList(CameraLookKind kind, CameraLookStatus status, String q) {
        String query = q == null || q.isBlank() ? null : q.trim();
        List<CameraLook> looks = cameraLookRepository.search(
                kind == null ? null : kind.name(), status == null ? null : status.name(), query, MAX_LOOKS);
        return withUsage(looks);
    }

    @Override
    @Transactional(readOnly = true)
    public CameraLookResponse adminGet(UUID lookId) {
        return withUsage(List.of(requireLook(lookId))).get(0);
    }

    @Override
    @Transactional
    public CameraLookResponse adminCreate(CreateCameraLookRequest request, UUID actorUserId) {
        CameraLookKind kind = request.getKind();
        String key = request.getKey().trim();
        if (cameraLookRepository.findByKindAndKey(kind.name(), key).isPresent()) {
            throw keyTaken(key);
        }
        requireWindow(request.getStartsAt(), request.getEndsAt());
        LocalDateTime now = LocalDateTime.now();
        CameraLook look = CameraLook.builder()
                .id(UUID.randomUUID())
                .kind(kind.name())
                .lookKey(key)
                .names(toNamesJson(request.getNames()))
                .definition(toDefinitionJson(kind, request.getDefinition()))
                .thumbnailUrl(requireOwnUrl(blankToNull(request.getThumbnailUrl())))
                .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
                .status((request.getStatus() == null ? CameraLookStatus.DRAFT : request.getStatus()).name())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .source(blankToNull(request.getSource()))
                .licenseNote(blankToNull(request.getLicenseNote()))
                .version(1)
                .createdBy(actorUserId)
                .updatedBy(actorUserId)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            cameraLookRepository.insert(look);
        } catch (DuplicateKeyException exception) {
            throw keyTaken(key);
        }
        return toResponse(look, 0L);
    }

    @Override
    @Transactional
    public CameraLookResponse adminUpdate(UUID lookId, UpdateCameraLookRequest request, UUID actorUserId) {
        CameraLook look = requireLook(lookId);
        if (request.getNames() != null) look.setNames(toNamesJson(request.getNames()));
        if (request.getDefinition() != null) {
            look.setDefinition(toDefinitionJson(CameraLookKind.valueOf(look.getKind()), request.getDefinition()));
        }
        if (request.getThumbnailUrl() != null) {
            look.setThumbnailUrl(requireOwnUrl(blankToNull(request.getThumbnailUrl())));
        }
        if (request.getSortOrder() != null) look.setSortOrder(request.getSortOrder());
        if (request.getStatus() != null) look.setStatus(request.getStatus().name());
        if (Boolean.TRUE.equals(request.getClearStartsAt())) {
            look.setStartsAt(null);
        } else if (request.getStartsAt() != null) {
            look.setStartsAt(request.getStartsAt());
        }
        if (Boolean.TRUE.equals(request.getClearEndsAt())) {
            look.setEndsAt(null);
        } else if (request.getEndsAt() != null) {
            look.setEndsAt(request.getEndsAt());
        }
        requireWindow(look.getStartsAt(), look.getEndsAt());
        if (request.getSource() != null) look.setSource(blankToNull(request.getSource()));
        if (request.getLicenseNote() != null) look.setLicenseNote(blankToNull(request.getLicenseNote()));

        look.setVersion((look.getVersion() == null ? 0 : look.getVersion()) + 1);
        look.setUpdatedBy(actorUserId);
        look.setUpdatedAt(LocalDateTime.now());
        if (cameraLookRepository.update(look) != 1) {
            throw notFound();
        }
        return withUsage(List.of(look)).get(0);
    }

    @Override
    @Transactional
    public List<CameraLookResponse> adminReorder(ReorderCameraLooksRequest request, UUID actorUserId) {
        String kind = request.getKind().name();
        LocalDateTime now = LocalDateTime.now();
        int position = 0;
        for (UUID lookId : request.getIds()) {
            if (cameraLookRepository.updateSortOrder(lookId, kind, position++, actorUserId, now) != 1) {
                // Thrown inside the transaction, so the looks already moved go back.
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "ids must all be camera looks of kind " + kind);
            }
        }
        return withUsage(cameraLookRepository.search(kind, null, null, MAX_LOOKS));
    }

    @Override
    @Transactional
    public void adminDelete(UUID lookId) {
        CameraLook look = requireLook(lookId);
        long usageCount = usageCounts(List.of(look)).getOrDefault(usageKey(look), 0L);
        if (usageCount > 0) {
            throw new BusinessException(ErrorConstant.CAMERA_LOOK_IN_USE,
                    "Camera look is used by " + usageCount + " check-in photo(s); archive it instead");
        }
        // Its files stay until the orphan sweep sees no row naming them.
        cameraLookRepository.delete(lookId);
    }

    @Override
    public CameraLookUploadResponse adminUpload(MultipartFile file, UUID actorUserId) {
        // PNG only: JPEG and WebP are lossy, and a LUT or a frame edge must arrive pixel for pixel.
        // The upload door checks the bytes against this declared type and stores them as sent.
        if (file == null || !MediaType.IMAGE_PNG_VALUE.equalsIgnoreCase(file.getContentType())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Only PNG files are allowed");
        }
        ImageUploadOutcome outcome = fileUploadService.uploadImage(
                ImageUploadRequest.of(actorUserId, ImageUploadRequest.ImageEntryPoint.CAMERA_LOOK, STORAGE_PREFIX),
                file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection()
                            ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION
                            : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }
        return new CameraLookUploadResponse(outcome.url());
    }

    @Override
    @Transactional(readOnly = true)
    public CameraLookManifestResponse publicManifest() {
        List<CameraLook> looks = cameraLookRepository.findActive(LocalDateTime.now(), MAX_LOOKS);
        List<Map<String, Object>> filters = new ArrayList<>();
        List<Map<String, Object>> frames = new ArrayList<>();
        List<Map<String, Object>> groups = new ArrayList<>();
        long newestUpdate = 0;
        for (CameraLook look : looks) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", look.getLookKey());
            entry.put("name", parseNames(look.getNames()));
            entry.putAll(parseDefinitionFields(look.getDefinition()));
            switch (CameraLookKind.valueOf(look.getKind())) {
                case FILTER -> filters.add(entry);
                case FRAME -> frames.add(entry);
                case GROUP -> groups.add(entry);
            }
            if (look.getUpdatedAt() != null) {
                newestUpdate = Math.max(newestUpdate, look.getUpdatedAt().toEpochSecond(ZoneOffset.UTC));
            }
        }
        return CameraLookManifestResponse.builder()
                // The newest edit catches a changed look; the count catches one leaving the set
                // (archived, deleted, or past its window) without any row being edited.
                .version(newestUpdate + "-" + looks.size())
                .filters(filters)
                .frames(frames)
                .groups(groups)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public String adminAnnounce() {
        String version = publicManifest().getVersion();
        firebaseService.sendDataToTopic(CHANGED_TOPIC, Map.of("type", CHANGED_TYPE, "version", version));
        return version;
    }

    private List<CameraLookResponse> withUsage(List<CameraLook> looks) {
        Map<String, Long> usage = usageCounts(looks);
        return looks.stream()
                .map(look -> toResponse(look, usage.getOrDefault(usageKey(look), 0L)))
                .toList();
    }

    /** Usage of each look, keyed by {@link #usageKey}: a filter and a frame may share a key. */
    private Map<String, Long> usageCounts(List<CameraLook> looks) {
        List<String> filterKeys = keysOf(looks, CameraLookKind.FILTER);
        List<String> frameKeys = keysOf(looks, CameraLookKind.FRAME);
        Map<String, Long> usage = new LinkedHashMap<>();
        userCheckinRepository.countPhotosByFilterKeys(filterKeys)
                .forEach(row -> usage.put(CameraLookKind.FILTER.name() + ":" + row.getLookKey(), row.getUsageCount()));
        userCheckinRepository.countPhotosByFrameKeys(frameKeys)
                .forEach(row -> usage.put(CameraLookKind.FRAME.name() + ":" + row.getLookKey(), row.getUsageCount()));
        return usage;
    }

    private static List<String> keysOf(List<CameraLook> looks, CameraLookKind kind) {
        return looks.stream()
                .filter(look -> kind.name().equals(look.getKind()))
                .map(CameraLook::getLookKey)
                .toList();
    }

    private static String usageKey(CameraLook look) {
        return look.getKind() + ":" + look.getLookKey();
    }

    private String toNamesJson(Map<String, String> names) {
        Map<String, String> cleaned = new LinkedHashMap<>();
        for (Map.Entry<String, String> name : names.entrySet()) {
            if (!NAME_LANGUAGES.contains(name.getKey())) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Unknown language '" + name.getKey() + "'; allowed: " + String.join(", ", NAME_LANGUAGES));
            }
            String value = blankToNull(name.getValue());
            if (value == null) {
                continue;
            }
            if (value.length() > MAX_NAME_LENGTH) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "A name is at most " + MAX_NAME_LENGTH + " characters");
            }
            cleaned.put(name.getKey(), value);
        }
        if (!cleaned.containsKey(REQUIRED_NAME_LANGUAGE)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "The English name (en) is required");
        }
        return JsonUtils.toJson(cleaned);
    }

    private String toDefinitionJson(CameraLookKind kind, JsonNode definition) {
        CameraLookDefinitionValidator.validate(kind, definition, this::isLookAssetUrl);
        return definition.toString();
    }

    /** A LUT or a frame image must be a file of the never-compressed prefix, nothing else. */
    private boolean isLookAssetUrl(String url) {
        String objectKey = isHttpUrl(url) ? storageService.extractObjectKey(url) : null;
        return objectKey != null && objectKey.startsWith(STORAGE_PREFIX + "/");
    }

    /** extractObjectKey also accepts a bare object key; the app needs a URL it can fetch. */
    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("https://") || url.startsWith("http://"));
    }

    /** A thumbnail may be any image already in our own storage. */
    private String requireOwnUrl(String url) {
        if (url != null && (!isHttpUrl(url) || storageService.extractObjectKey(url) == null)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "thumbnailUrl must point to our own storage; upload the image instead");
        }
        return url;
    }

    private static void requireWindow(LocalDateTime startsAt, LocalDateTime endsAt) {
        if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "endsAt must be after startsAt");
        }
    }

    private CameraLook requireLook(UUID lookId) {
        return cameraLookRepository.findById(lookId).orElseThrow(CameraLookServiceImpl::notFound);
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorConstant.CAMERA_LOOK_NOT_FOUND, "Camera look not found");
    }

    private static BusinessException keyTaken(String key) {
        return new BusinessException(ErrorConstant.CAMERA_LOOK_KEY_TAKEN,
                "Another camera look of this kind already uses the key '" + key + "'");
    }

    private static CameraLookResponse toResponse(CameraLook look, long usageCount) {
        return CameraLookResponse.builder()
                .id(look.getId())
                .kind(look.getKind())
                .key(look.getLookKey())
                .names(parseNames(look.getNames()))
                .definition(JsonUtils.fromJson(look.getDefinition(), JsonNode.class))
                .thumbnailUrl(look.getThumbnailUrl())
                .sortOrder(look.getSortOrder())
                .status(look.getStatus())
                .startsAt(look.getStartsAt())
                .endsAt(look.getEndsAt())
                .source(look.getSource())
                .licenseNote(look.getLicenseNote())
                .version(look.getVersion())
                .usageCount(usageCount)
                .createdAt(look.getCreatedAt())
                .updatedAt(look.getUpdatedAt())
                .build();
    }

    private static Map<String, String> parseNames(String json) {
        Map<String, String> names = json == null || json.isBlank()
                ? null
                : JsonUtils.fromJson(json, new TypeReference<LinkedHashMap<String, String>>() {});
        return names == null ? Map.of() : names;
    }

    private static Map<String, Object> parseDefinitionFields(String json) {
        Map<String, Object> fields = json == null || json.isBlank()
                ? null
                : JsonUtils.fromJson(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        return fields == null ? Map.of() : fields;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
