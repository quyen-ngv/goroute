package com.ds.goroute.service.place;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.PlaceHoursDay;
import com.ds.goroute.dto.request.CreatePlaceEditSuggestionRequest;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PlaceEditContextResponse;
import com.ds.goroute.dto.response.PlaceEditSuggestionResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditGuard;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.service.StorageService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.PlaceBusinessStatus;
import com.ds.goroute.type.PlaceEditAuthor;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import com.ds.goroute.type.PlaceEditUnavailableReason;
import com.ds.goroute.type.TranslationLocale;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The app side of place edit suggestions (plan §3.6, §3.10): what the form needs, sending,
 * the submitter's own list, and cancelling.
 */
@Service
@RequiredArgsConstructor
public class PlaceEditSuggestionService {

    /** A suggestion with no field change needs at least this much description. */
    public static final int MIN_DESCRIPTION_ONLY_LENGTH = 10;
    public static final int MAX_PAGE_SIZE = 50;

    private final PlaceEditAccess access;
    private final PlaceEditApplier applier;
    private final PlaceEditResponseAssembler assembler;
    private final PlaceEditSuggestionRepository suggestions;
    private final PlaceRepository placeRepository;
    private final PlaceTranslationService placeTranslationService;
    private final BusinessConfigService config;
    private final StorageService storageService;
    private final ObjectMapper objectMapper;

    Clock clock = Clock.systemDefaultZone();

    @Transactional(readOnly = true)
    public PlaceEditContextResponse context(UUID userId, UUID placeId) {
        PlaceEditGuard guard = placeRepository.findEditGuard(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        PlaceEditUnavailableReason reason = access.unavailableReason(userId, guard, LocalDateTime.now(clock));
        List<PlaceHoursDay> hours = PlaceOpenHoursFormatter.parse(readHours(place.getOpenHours()));
        List<PlaceEditContextResponse.PendingChange> pending = suggestions
                .findPendingChangesOfUserForPlace(userId, placeId).stream()
                .map(change -> PlaceEditContextResponse.PendingChange.builder()
                        .suggestionId(change.getSuggestionId())
                        .changeId(change.getId())
                        .field(change.getField().name())
                        .newValue(assembler.readTree(change.getNewValue()))
                        .build())
                .toList();
        return PlaceEditContextResponse.builder()
                .enabled(access.isEnabled(userId))
                .allowed(reason == null)
                .reason(reason == null ? null : reason.name())
                .businessStatus(PlaceBusinessStatus.fromStoredStatus(place.getStatus()).name())
                .lockedFields(placeRepository.findLockedFields(placeId))
                .hoursStructured(hours)
                .priceRange(PlacePriceRangeFormatter.parse(place.getPriceRange()))
                .pendingMine(pending)
                .build();
    }

    @Transactional
    public PlaceEditSuggestionResponse submit(UUID userId, UUID placeId, CreatePlaceEditSuggestionRequest request) {
        access.requireEnabled(userId);
        LocalDateTime now = LocalDateTime.now(clock);
        PlaceEditGuard guard = placeRepository.findEditGuard(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        if (request.getType() == PlaceEditType.ADD_PHOTOS) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Adding photos is not available yet");
        }
        if (!PlaceEditAccess.isVisible(guard) || access.isAccountTooNew(userId, now)) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_NOT_ALLOWED);
        }

        Map<PlaceEditField, ObjectNode> requested = requestedChanges(request);
        boolean onlyStatus = requested.keySet().stream().allMatch(field -> field == PlaceEditField.BUSINESS_STATUS);
        if (guard.isPartnerPlace() && !onlyStatus) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_NOT_ALLOWED);
        }
        if (request.getType() == PlaceEditType.STATUS_REPORT
                && (!onlyStatus || !requested.containsKey(PlaceEditField.BUSINESS_STATUS))) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A status report carries businessStatus only");
        }
        requireAllowance(userId, now);

        String locale = TranslationLocale.fromCode(AcceptLanguageFilter.currentCode())
                .orElse(TranslationLocale.DEFAULT).code();
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        List<PlaceTranslation> translations = translationsOf(placeId);
        PlaceEditPlaceState state = new PlaceEditPlaceState(place, translations, locale);

        Map<PlaceEditField, ObjectNode> changes = new LinkedHashMap<>();
        requested.forEach((field, value) -> {
            ObjectNode normalized = applier.normalize(field, value, state, true);
            if (!applier.isUnchanged(field, normalized, state)) {
                changes.put(field, normalized);
            }
        });
        String description = blankToNull(request.getDescription());
        if (changes.isEmpty() && (description == null || description.length() < MIN_DESCRIPTION_ONLY_LENGTH)) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_NO_CHANGES);
        }

        PlaceEditSuggestion suggestion = PlaceEditSuggestion.builder()
                .id(UUID.randomUUID())
                .placeId(placeId)
                .userId(userId)
                .type(request.getType())
                .status(PlaceEditStatus.PENDING)
                .description(description)
                .evidencePhotos(assembler.toJson(storagePhotos(request.getEvidencePhotos())))
                .locale(locale)
                .createdAt(now)
                .updatedAt(now)
                .build();
        suggestions.insertSuggestion(suggestion);

        List<UUID> emptiedSuggestionIds = new ArrayList<>();
        for (Map.Entry<PlaceEditField, ObjectNode> entry : changes.entrySet()) {
            PlaceEditField field = entry.getKey();
            String oldValue = assembler.toJson(applier.currentValue(field, state, entry.getValue()));
            String newValue = assembler.toJson(entry.getValue());
            PlaceEditChange mine = suggestions.findPendingUserChange(userId, placeId, field).orElse(null);
            if (mine != null) {
                // One pending value per person and field: the newer suggestion takes the row over.
                suggestions.moveChange(mine.getId(), suggestion.getId(), oldValue, newValue, now);
                emptiedSuggestionIds.add(mine.getSuggestionId());
                continue;
            }
            suggestions.insertChange(PlaceEditChange.builder()
                    .id(UUID.randomUUID())
                    .suggestionId(suggestion.getId())
                    .placeId(placeId)
                    .userId(userId)
                    .field(field)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .author(PlaceEditAuthor.USER)
                    .status(PlaceEditChangeStatus.PENDING)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        }
        cancelEmptied(userId, emptiedSuggestionIds, now);

        List<PlaceEditChange> stored = suggestions.findChangesBySuggestionIds(List.of(suggestion.getId()))
                .getOrDefault(suggestion.getId(), List.of());
        return assembler.suggestion(suggestion, stored, place, translations, locale, false);
    }

    @Transactional(readOnly = true)
    public PageResponse<PlaceEditSuggestionResponse> mine(UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(MAX_PAGE_SIZE, size));
        List<PlaceEditSuggestion> rows = suggestions.findByUser(userId, safeSize, safePage * safeSize);
        long total = suggestions.countByUser(userId);
        return PageResponse.of(responses(rows), total, safePage, safeSize);
    }

    @Transactional
    public void cancel(UUID userId, UUID suggestionId) {
        PlaceEditSuggestion suggestion = suggestions.findSuggestion(suggestionId)
                .filter(found -> userId.equals(found.getUserId()))
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_EDIT_NOT_FOUND));
        LocalDateTime now = LocalDateTime.now(clock);
        if (suggestion.getStatus() != PlaceEditStatus.PENDING
                || !suggestions.cancel(suggestionId, userId, now)) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_ALREADY_REVIEWED);
        }
        suggestions.cancelChangesOfSuggestion(suggestionId, now);
    }

    /** The responses of a page of suggestions, places and their names loaded once for all of them. */
    private List<PlaceEditSuggestionResponse> responses(List<PlaceEditSuggestion> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        List<UUID> placeIds = rows.stream().map(PlaceEditSuggestion::getPlaceId).distinct().toList();
        Map<UUID, Place> places = placeRepository.findListSummaryByIds(placeIds, false).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity(), (a, b) -> a));
        Map<UUID, List<PlaceTranslation>> translations = placeTranslationService.findByPlaceIds(placeIds);
        Map<UUID, List<PlaceEditChange>> changes = suggestions.findChangesBySuggestionIds(
                rows.stream().map(PlaceEditSuggestion::getId).toList());
        String locale = AcceptLanguageFilter.currentCode();
        return rows.stream()
                .map(row -> assembler.suggestion(row, changes.getOrDefault(row.getId(), List.of()),
                        places.get(row.getPlaceId()), translations.getOrDefault(row.getPlaceId(), List.of()),
                        locale, false))
                .toList();
    }

    /** The submission's fields as §3.2 JSON values, before normalization. */
    Map<PlaceEditField, ObjectNode> requestedChanges(CreatePlaceEditSuggestionRequest request) {
        Map<PlaceEditField, ObjectNode> result = new LinkedHashMap<>();
        CreatePlaceEditSuggestionRequest.Changes changes = request.getChanges();
        if (changes != null) {
            putText(result, PlaceEditField.NAME, "name", changes.getName());
            putText(result, PlaceEditField.ALIAS, "alias", changes.getAlias());
            putText(result, PlaceEditField.DESCRIPTION, "description", changes.getPlaceDescription());
            if (changes.getCategory() != null) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("placeGroup", changes.getCategory().getPlaceGroup() == null
                        ? null : changes.getCategory().getPlaceGroup().name());
                var subTypes = node.putArray("subTypes");
                if (changes.getCategory().getSubTypes() != null) {
                    changes.getCategory().getSubTypes().stream().filter(Objects::nonNull)
                            .forEach(kind -> subTypes.add(kind.name()));
                }
                result.put(PlaceEditField.CATEGORY, node);
            }
            putText(result, PlaceEditField.ADDRESS, "address", changes.getAddress());
            if (changes.getLocation() != null) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("latitude", changes.getLocation().getLatitude());
                node.put("longitude", changes.getLocation().getLongitude());
                result.put(PlaceEditField.LOCATION, node);
            }
            if (changes.getOpenHours() != null) {
                ObjectNode node = objectMapper.createObjectNode();
                node.set("structured", objectMapper.valueToTree(changes.getOpenHours()));
                result.put(PlaceEditField.OPEN_HOURS, node);
            }
            putText(result, PlaceEditField.PHONE, "phone", changes.getPhone());
            putText(result, PlaceEditField.WEBSITE, "website", changes.getWebsite());
            if (changes.getPriceRange() != null) {
                result.put(PlaceEditField.PRICE_RANGE, objectMapper.valueToTree(changes.getPriceRange()));
            }
            if (changes.getBusinessStatus() != null) {
                ObjectNode node = objectMapper.createObjectNode();
                node.put("reason", changes.getBusinessStatus().getReason() == null
                        ? null : changes.getBusinessStatus().getReason().name());
                node.put("duplicateOfPlaceId", changes.getBusinessStatus().getDuplicateOfPlaceId() == null
                        ? null : changes.getBusinessStatus().getDuplicateOfPlaceId().toString());
                result.put(PlaceEditField.BUSINESS_STATUS, node);
            }
        }
        if (request.getClearFields() != null) {
            for (PlaceEditField field : request.getClearFields()) {
                if (field == PlaceEditField.PHONE) {
                    result.put(field, objectMapper.createObjectNode().putNull("phone"));
                } else if (field == PlaceEditField.WEBSITE) {
                    result.put(field, objectMapper.createObjectNode().putNull("website"));
                } else if (field != null) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                            "Only PHONE and WEBSITE can be cleared");
                }
            }
        }
        return result;
    }

    private void putText(Map<PlaceEditField, ObjectNode> result, PlaceEditField field, String key, String value) {
        if (value != null) {
            result.put(field, objectMapper.createObjectNode().put(key, value));
        }
    }

    private void requireAllowance(UUID userId, LocalDateTime now) {
        if (suggestions.countSubmittedSince(userId, now.minusHours(24))
                >= config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_SUBMISSIONS_PER_DAY)
                || suggestions.countPendingByUser(userId)
                >= config.getInt(BusinessConfigKey.PLACE_EDIT_MAX_PENDING_PER_USER)) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_RATE_LIMITED);
        }
    }

    /**
     * An older suggestion whose only content moved to the new one is left with nothing to review:
     * no change and no description. It is cancelled rather than left in the queue empty.
     */
    private void cancelEmptied(UUID userId, List<UUID> suggestionIds, LocalDateTime now) {
        if (suggestionIds.isEmpty()) {
            return;
        }
        Map<UUID, List<PlaceEditChange>> remaining = suggestions.findChangesBySuggestionIds(suggestionIds);
        for (UUID id : suggestionIds.stream().distinct().toList()) {
            boolean hasPendingChange = remaining.getOrDefault(id, List.of()).stream()
                    .anyMatch(change -> change.getStatus() == PlaceEditChangeStatus.PENDING);
            PlaceEditSuggestion older = suggestions.findSuggestion(id).orElse(null);
            if (older != null && !hasPendingChange && blankToNull(older.getDescription()) == null) {
                suggestions.cancel(id, userId, now);
            }
        }
    }

    private List<String> storagePhotos(List<String> photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(photo -> storageService.extractObjectKey(photo) != null)
                .distinct()
                .limit(3)
                .toList();
    }

    private List<PlaceTranslation> translationsOf(UUID placeId) {
        return placeTranslationService.findByPlaceIds(List.of(placeId)).getOrDefault(placeId, List.of());
    }

    private Map<String, List<String>> readHours(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (com.fasterxml.jackson.core.JsonProcessingException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
