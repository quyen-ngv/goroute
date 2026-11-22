package com.ds.goroute.service.place;

import com.ds.goroute.config.filter.AcceptLanguageFilter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.ReviewPlaceEditSuggestionRequest;
import com.ds.goroute.dto.response.AdminPlaceEditDetailResponse;
import com.ds.goroute.dto.response.AdminPlaceEditListItem;
import com.ds.goroute.dto.response.PageResponse;
import com.ds.goroute.dto.response.PlaceEditCountResponse;
import com.ds.goroute.entity.Place;
import com.ds.goroute.entity.PlaceEditChange;
import com.ds.goroute.entity.PlaceEditSubmitterStats;
import com.ds.goroute.entity.PlaceEditSuggestion;
import com.ds.goroute.entity.PlaceTranslation;
import com.ds.goroute.entity.User;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.repository.PlaceContributorRepository;
import com.ds.goroute.repository.PlaceEditSuggestionRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.repository.UserRepository;
import com.ds.goroute.service.PlaceAttributeCatalog;
import com.ds.goroute.service.PlaceTranslationService;
import com.ds.goroute.type.PlaceBusinessStatus;
import com.ds.goroute.type.PlaceEditAuthor;
import com.ds.goroute.type.PlaceEditChangeStatus;
import com.ds.goroute.type.PlaceEditDecisionAction;
import com.ds.goroute.type.PlaceEditField;
import com.ds.goroute.type.PlaceEditRejectReason;
import com.ds.goroute.type.PlaceEditReviewChannel;
import com.ds.goroute.type.PlaceEditStatus;
import com.ds.goroute.type.PlaceEditType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The console side of place edit suggestions (plan §3.8): the queue, one suggestion with the
 * place as it is now, the review that applies what the operator approves, and unlocking a field.
 */
@Service
@RequiredArgsConstructor
public class PlaceEditReviewService {

    public static final int DESCRIPTION_PREVIEW_LENGTH = 140;

    private final PlaceEditSuggestionRepository suggestions;
    private final PlaceRepository placeRepository;
    private final PlaceContributorRepository contributors;
    private final PlaceTranslationService placeTranslationService;
    private final UserRepository users;
    private final PlaceEditApplier applier;
    private final PlaceEditResponseAssembler assembler;
    private final PlaceEditNotifier notifier;
    private final ObjectMapper objectMapper;

    Clock clock = Clock.systemDefaultZone();

    @Transactional(readOnly = true)
    public PageResponse<AdminPlaceEditListItem> list(PlaceEditStatus status, PlaceEditType type, UUID placeId,
                                                     UUID userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        List<PlaceEditSuggestion> rows = suggestions.findAdminPage(status, type, placeId, userId,
                safeSize, safePage * safeSize);
        long total = suggestions.countAdmin(status, type, placeId, userId);
        if (rows.isEmpty()) {
            return PageResponse.of(List.of(), total, safePage, safeSize);
        }
        List<UUID> placeIds = rows.stream().map(PlaceEditSuggestion::getPlaceId).distinct().toList();
        Map<UUID, Place> places = placesById(placeIds);
        Map<UUID, List<PlaceTranslation>> translations = placeTranslationService.findByPlaceIds(placeIds);
        Map<UUID, User> people = usersById(rows.stream().map(PlaceEditSuggestion::getUserId).toList());
        Map<UUID, List<PlaceEditChange>> changes = suggestions.findChangesBySuggestionIds(
                rows.stream().map(PlaceEditSuggestion::getId).toList());
        String locale = AcceptLanguageFilter.currentCode();
        List<AdminPlaceEditListItem> items = rows.stream().map(row -> {
            Place place = places.get(row.getPlaceId());
            User user = people.get(row.getUserId());
            return AdminPlaceEditListItem.builder()
                    .id(row.getId())
                    .placeId(row.getPlaceId())
                    .placeName(PlaceEditResponseAssembler.placeName(place,
                            translations.getOrDefault(row.getPlaceId(), List.of()), locale))
                    .placeAddress(place == null ? null : place.getAddress())
                    .placeThumbnail(place == null ? null : place.getThumbnail())
                    .userId(row.getUserId())
                    .userName(nameOf(user))
                    .userAvatar(user == null ? null : user.getAvatarUrl())
                    .type(row.getType().name())
                    .status(row.getStatus().name())
                    .fields(fieldsOf(changes.getOrDefault(row.getId(), List.of())))
                    .descriptionPreview(preview(row.getDescription()))
                    .evidencePhotoCount(assembler.photos(row.getEvidencePhotos()).size())
                    .createdAt(row.getCreatedAt())
                    .reviewedAt(row.getReviewedAt())
                    .build();
        }).toList();
        return PageResponse.of(items, total, safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public PlaceEditCountResponse count(PlaceEditStatus status) {
        return new PlaceEditCountResponse(suggestions.countAdmin(status, null, null, null));
    }

    @Transactional(readOnly = true)
    public AdminPlaceEditDetailResponse detail(UUID suggestionId) {
        PlaceEditSuggestion suggestion = suggestions.findSuggestion(suggestionId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_EDIT_NOT_FOUND));
        return detailOf(suggestion);
    }

    /**
     * Applies an operator's decisions in one transaction (plan §3.8). Every pending change needs a
     * decision; operator changes read from the description are applied as they are added.
     */
    @Transactional
    public AdminPlaceEditDetailResponse review(UUID suggestionId, ReviewPlaceEditSuggestionRequest request,
                                               UUID reviewerId, PlaceEditReviewChannel channel) {
        PlaceEditSuggestion suggestion = suggestions.lockSuggestion(suggestionId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_EDIT_NOT_FOUND));
        if (suggestion.getStatus() != PlaceEditStatus.PENDING) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_ALREADY_REVIEWED);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        UUID placeId = suggestion.getPlaceId();
        List<PlaceEditChange> pending = suggestions.findChangesBySuggestionIds(List.of(suggestionId))
                .getOrDefault(suggestionId, List.of()).stream()
                .filter(change -> change.getStatus() == PlaceEditChangeStatus.PENDING)
                .toList();
        Map<UUID, ReviewPlaceEditSuggestionRequest.Decision> decisions = decisionsCovering(pending, request);
        if (placeRepository.findEditGuard(placeId).isEmpty()) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }

        int applied = 0;
        int rejected = 0;
        Set<PlaceEditField> appliedFields = new LinkedHashSet<>();
        for (PlaceEditChange change : pending) {
            ReviewPlaceEditSuggestionRequest.Decision decision = decisions.get(change.getId());
            if (decision.getAction() == PlaceEditDecisionAction.REJECT) {
                PlaceEditRejectReason reason = decision.getRejectReason() == null
                        ? PlaceEditRejectReason.OTHER : decision.getRejectReason();
                suggestions.decideChange(change.getId(), PlaceEditChangeStatus.REJECTED, null, reason, now);
                rejected++;
                continue;
            }
            JsonNode value = isAbsent(decision.getAppliedValue())
                    ? assembler.readTree(change.getNewValue())
                    : decision.getAppliedValue();
            String locale = decision.getLocale() == null || decision.getLocale().isBlank()
                    ? suggestion.getLocale() : decision.getLocale();
            PlaceEditPlaceState state = state(placeId, locale);
            ObjectNode normalized = applier.normalize(change.getField(), value, state,
                    change.getAuthor() == PlaceEditAuthor.USER && isAbsent(decision.getAppliedValue()));
            applier.apply(change.getField(), normalized, state, locale);
            suggestions.decideChange(change.getId(), PlaceEditChangeStatus.APPROVED,
                    assembler.toJson(normalized), null, now);
            appliedFields.add(change.getField());
            applied++;
        }
        if (request.getAdminChanges() != null) {
            for (ReviewPlaceEditSuggestionRequest.AdminChange adminChange : request.getAdminChanges()) {
                String locale = adminChange.getLocale() == null || adminChange.getLocale().isBlank()
                        ? suggestion.getLocale() : adminChange.getLocale();
                PlaceEditPlaceState state = state(placeId, locale);
                ObjectNode normalized = applier.normalize(adminChange.getField(), adminChange.getValue(), state, false);
                String oldValue = assembler.toJson(applier.currentValue(adminChange.getField(), state, normalized));
                applier.apply(adminChange.getField(), normalized, state, locale);
                String json = assembler.toJson(normalized);
                suggestions.insertChange(PlaceEditChange.builder()
                        .id(UUID.randomUUID())
                        .suggestionId(suggestionId)
                        .placeId(placeId)
                        .userId(suggestion.getUserId())
                        .field(adminChange.getField())
                        .oldValue(oldValue)
                        .newValue(json)
                        .appliedValue(json)
                        .author(PlaceEditAuthor.ADMIN)
                        .status(PlaceEditChangeStatus.APPROVED)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());
                appliedFields.add(adminChange.getField());
                applied++;
            }
        }
        for (PlaceEditField field : appliedFields) {
            suggestions.supersedePending(placeId, field, suggestionId, now);
        }

        PlaceEditStatus status;
        PlaceEditRejectReason rejectReason = null;
        if (applied == 0) {
            if (request.getRejectReason() == null) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "rejectReason is required when nothing is applied");
            }
            status = PlaceEditStatus.REJECTED;
            rejectReason = request.getRejectReason();
        } else {
            status = rejected > 0 ? PlaceEditStatus.PARTIALLY_APPROVED : PlaceEditStatus.APPROVED;
        }
        if (!suggestions.recordReview(suggestionId, status, rejectReason, blankToNull(request.getNote()),
                channel, reviewerId, now)) {
            throw new BusinessException(ErrorConstant.PLACE_EDIT_ALREADY_REVIEWED);
        }
        if (applied > 0) {
            contributors.credit(placeId, suggestion.getUserId());
        }
        Place place = placeRepository.findById(placeId).orElse(null);
        notifier.afterReview(suggestionId, placeId, suggestion.getUserId(), applied > 0,
                rejectReason == null ? null : rejectReason.name(), place == null ? null : place.getTitle(),
                translationsOf(placeId), reviewerId);
        return detailOf(suggestions.findSuggestion(suggestionId).orElseThrow());
    }

    /** {@code DELETE /admin/places/{placeId}/locked-fields/{field}}: the re-scrape may write it again. */
    @Transactional
    public List<String> unlockField(UUID placeId, PlaceEditField field) {
        if (!placeRepository.existsById(placeId)) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND);
        }
        placeRepository.removeLockedField(placeId, field.name());
        return placeRepository.findLockedFields(placeId);
    }

    private AdminPlaceEditDetailResponse detailOf(PlaceEditSuggestion suggestion) {
        UUID placeId = suggestion.getPlaceId();
        Place place = placeRepository.findById(placeId).orElse(null);
        List<PlaceTranslation> translations = translationsOf(placeId);
        String locale = AcceptLanguageFilter.currentCode();
        List<PlaceEditChange> changes = suggestions.findChangesBySuggestionIds(List.of(suggestion.getId()))
                .getOrDefault(suggestion.getId(), List.of());
        User submitter = users.findById(suggestion.getUserId()).orElse(null);
        PlaceEditSubmitterStats stats = suggestions.findSubmitterStats(suggestion.getUserId());
        User reviewer = suggestion.getReviewedBy() == null ? null
                : users.findById(suggestion.getReviewedBy()).orElse(null);

        List<PlaceEditSuggestion> others = suggestions.findOtherPendingForPlace(placeId, suggestion.getId());
        Map<UUID, User> otherPeople = usersById(others.stream().map(PlaceEditSuggestion::getUserId).toList());
        Map<UUID, List<PlaceEditChange>> otherChanges = suggestions.findChangesBySuggestionIds(
                others.stream().map(PlaceEditSuggestion::getId).toList());

        return AdminPlaceEditDetailResponse.builder()
                .suggestion(assembler.suggestion(suggestion, changes, place, translations, locale, true))
                .submitter(AdminPlaceEditDetailResponse.Submitter.builder()
                        .id(suggestion.getUserId())
                        .name(nameOf(submitter))
                        .avatar(submitter == null ? null : submitter.getAvatarUrl())
                        .createdAt(submitter == null ? null : submitter.getCreatedAt())
                        .approvedCount(stats.getApprovedCount())
                        .rejectedCount(stats.getRejectedCount())
                        .build())
                .reviewer(suggestion.getReviewedBy() == null ? null : AdminPlaceEditDetailResponse.Reviewer.builder()
                        .id(suggestion.getReviewedBy())
                        .name(nameOf(reviewer))
                        .build())
                .reviewChannel(suggestion.getReviewChannel() == null ? null : suggestion.getReviewChannel().name())
                .place(place == null ? null : snapshot(place, translations, locale))
                .otherPending(others.stream().map(other -> AdminPlaceEditDetailResponse.OtherPending.builder()
                        .id(other.getId())
                        .userName(nameOf(otherPeople.get(other.getUserId())))
                        .fields(fieldsOf(otherChanges.getOrDefault(other.getId(), List.of())))
                        .createdAt(other.getCreatedAt())
                        .build()).toList())
                .build();
    }

    private AdminPlaceEditDetailResponse.PlaceSnapshot snapshot(Place place, List<PlaceTranslation> translations,
                                                                String locale) {
        PlaceEditPlaceState state = new PlaceEditPlaceState(place, translations, locale);
        JsonNode hours = applier.currentValue(PlaceEditField.OPEN_HOURS, state, null);
        Map<String, List<String>> openHours = hours.get("display") == null || hours.get("display").isNull()
                ? null
                : objectMapper.convertValue(hours.get("display"),
                        new com.fasterxml.jackson.core.type.TypeReference<Map<String, List<String>>>() {});
        return AdminPlaceEditDetailResponse.PlaceSnapshot.builder()
                .id(place.getId())
                .title(place.getTitle())
                .displayName(state.displayName())
                .originalName(state.originalName())
                .googleMapsLink(place.getGoogleMapsLink())
                .address(place.getAddress())
                .latitude(place.getLatitude())
                .longitude(place.getLongitude())
                .hasVerificationGeometry(place.getVerificationGeometryGeoJson() != null
                        && !place.getVerificationGeometryGeoJson().isBlank())
                .placeGroup(place.getPlaceGroup() == null ? null : place.getPlaceGroup().name())
                .subTypes(place.getSubTypes() == null ? List.of() : List.of(place.getSubTypes()))
                .phone(place.getPhone())
                .website(place.getWebsite())
                .priceRange(place.getPriceRange())
                .openHours(openHours)
                .hoursStructured(PlaceOpenHoursFormatter.parse(openHours))
                .description(state.displayDescription())
                .searchAliases(place.getSearchAliases() == null ? List.of() : List.of(place.getSearchAliases()))
                .status(place.getStatus())
                .businessStatus(PlaceBusinessStatus.fromStoredStatus(place.getStatus()).name())
                .visibilityStatus(place.getVisibilityStatus() == null ? null : place.getVisibilityStatus().name())
                .lockedFields(placeRepository.findLockedFields(place.getId()))
                .attributes(PlaceAttributeCatalog.adminAttributes(assembler.readTree(place.getAttributes())))
                .build();
    }

    /** One decision per pending change: none missing, none unknown, none twice. */
    private static Map<UUID, ReviewPlaceEditSuggestionRequest.Decision> decisionsCovering(
            List<PlaceEditChange> pending, ReviewPlaceEditSuggestionRequest request) {
        Map<UUID, ReviewPlaceEditSuggestionRequest.Decision> byChange = new HashMap<>();
        Set<UUID> pendingIds = new HashSet<>();
        pending.forEach(change -> pendingIds.add(change.getId()));
        if (request.getDecisions() != null) {
            for (ReviewPlaceEditSuggestionRequest.Decision decision : request.getDecisions()) {
                if (!pendingIds.contains(decision.getChangeId())
                        || byChange.put(decision.getChangeId(), decision) != null) {
                    throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                            "Decision for an unknown or repeated change " + decision.getChangeId());
                }
            }
        }
        if (!byChange.keySet().equals(pendingIds)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Every pending change needs a decision");
        }
        return byChange;
    }

    /** The place read again before each change, so one approval sees the previous one's write. */
    private PlaceEditPlaceState state(UUID placeId, String locale) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.PLACE_NOT_FOUND));
        return new PlaceEditPlaceState(place, translationsOf(placeId), locale);
    }

    private List<PlaceTranslation> translationsOf(UUID placeId) {
        return placeTranslationService.findByPlaceIds(List.of(placeId)).getOrDefault(placeId, List.of());
    }

    private Map<UUID, Place> placesById(List<UUID> placeIds) {
        return placeRepository.findListSummaryByIds(placeIds, false).stream()
                .collect(Collectors.toMap(Place::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, User> usersById(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findByIds(userIds.stream().distinct().toList()).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private static List<String> fieldsOf(List<PlaceEditChange> changes) {
        List<String> fields = new ArrayList<>();
        for (PlaceEditChange change : changes) {
            if (change.getField() != null && !fields.contains(change.getField().name())) {
                fields.add(change.getField().name());
            }
        }
        return fields;
    }

    private static String nameOf(User user) {
        if (user == null) {
            return null;
        }
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    private static String preview(String description) {
        if (description == null) {
            return null;
        }
        String flat = description.replaceAll("\\s+", " ").trim();
        return flat.length() <= DESCRIPTION_PREVIEW_LENGTH ? flat : flat.substring(0, DESCRIPTION_PREVIEW_LENGTH - 1) + "…";
    }

    private static boolean isAbsent(JsonNode node) {
        return node == null || node.isNull() || node.isMissingNode();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
