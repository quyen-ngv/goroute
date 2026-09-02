package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.UpsertPassportDefinitionRequest;
import com.ds.goroute.dto.request.UpsertPassportTagRequest;
import com.ds.goroute.dto.request.UpsertPassportRewardRequest;
import com.ds.goroute.dto.request.UpsertPassportStampRuleRequest;
import com.ds.goroute.dto.response.PassportDefinitionResponse;
import com.ds.goroute.dto.response.PassportLocationMapEntryResponse;
import com.ds.goroute.dto.response.PassportProvinceOptionResponse;
import com.ds.goroute.dto.response.PassportStampProgressResponse;
import com.ds.goroute.dto.response.PassportStampResponse;
import com.ds.goroute.dto.response.PassportSummaryResponse;
import com.ds.goroute.dto.response.PassportTagResponse;
import com.ds.goroute.dto.response.PlacePassportTagsResponse;
import com.ds.goroute.dto.response.PassportRewardResponse;
import com.ds.goroute.dto.response.PassportStampRuleResponse;
import com.ds.goroute.dto.response.ProvinceMapEntryResponse;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.entity.PassportDefinition;
import com.ds.goroute.entity.PassportReward;
import com.ds.goroute.entity.PassportStamp;
import com.ds.goroute.entity.PassportStampRule;
import com.ds.goroute.entity.PassportTag;
import com.ds.goroute.entity.Province;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.entity.UserPassportTag;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.FileUploadService;
import com.ds.goroute.service.ImageUploadOutcome;
import com.ds.goroute.service.ImageUploadRequest;
import com.ds.goroute.service.PassportService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinVerificationStatus;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PassportServiceImpl implements PassportService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final String SOURCE_USER_CHECKIN = "USER_CHECKIN";
    private static final String SOURCE_ACTIVITY_CHECKIN = "ACTIVITY_CHECKIN";

    private final PassportMapper passportMapper;
    private final StarService pointWallet;
    private final BusinessConfigService config;
    private final FileUploadService fileUploadService;

    @Override
    @Transactional
    public void recordCheckin(UserCheckin checkin) {
        if (!isPassportEnabled()) {
            return;
        }
        PassportEvent event = PassportEvent.builder()
                .id(UUID.randomUUID())
                .userId(checkin.getUserId())
                .source(SOURCE_USER_CHECKIN)
                .sourceId(checkin.getId())
                .placeId(checkin.getPlaceId())
                .checkinId(checkin.getId())
                .locationKey(checkin.getLocationKey())
                .locationName(checkin.getLocationName())
                .latitude(checkin.getLatitude())
                .longitude(checkin.getLongitude())
                // Catalogued Place is authoritative; otherwise resolve only against
                // official aliases. An uncertain free-text location gets no province
                // rather than a misleading city proof tag.
                .provinceCode(resolveProvinceCode(checkin))
                .occurredAt(checkin.getCreatedAt())
                .isVerified(checkin.getVerificationStatus() == CheckinVerificationStatus.VERIFIED)
                .isHidden(false)
                .createdAt(LocalDateTime.now())
                .build();

        if (passportMapper.insertEvent(event) == 0) {
            // Already recorded. Re-running the projection must not award a second time.
            return;
        }
        evaluateStamps(checkin.getUserId(), event.getId());
        evaluateTags(checkin.getUserId(), event);
    }

    @Override
    @Transactional
    public int backfillLegacyActivityCheckins(int batchSize) {
        List<Map<String, Object>> legacy = passportMapper.findLegacyActivityCheckins(
                Math.max(1, Math.min(batchSize, 500)));
        int created = 0;
        Set<UUID> touchedUsers = new HashSet<>();

        for (Map<String, Object> row : legacy) {
            UUID userId = (UUID) row.get("user_id");
            PassportEvent event = PassportEvent.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .source(SOURCE_ACTIVITY_CHECKIN)
                    .sourceId((UUID) row.get("checkin_id"))
                    .placeId((UUID) row.get("place_id"))
                    .locationName(asString(row.get("location_name")))
                    .latitude(asBigDecimal(row.get("lat")))
                    .longitude(asBigDecimal(row.get("lng")))
                    .provinceCode(asString(row.get("province_code")))
                    .occurredAt(row.get("checked_in_at") instanceof LocalDateTime at ? at : LocalDateTime.now())
                    // An old activity check-in passed the radius test at the time it was
                    // made. Treating it as unverified now would take something away from
                    // long-standing users for a reason that is not their doing.
                    .isVerified(true)
                    .isHidden(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            if (passportMapper.insertEvent(event) > 0) {
                created++;
                touchedUsers.add(userId);
                evaluateTags(userId, event);
            }
        }
        touchedUsers.forEach(userId -> evaluateStamps(userId, null));
        return created;
    }

    @Override
    // getWallet() lazily creates the user's wallet when it does not exist, so this
    // read model must not run inside a read-only transaction.
    @Transactional
    public PassportSummaryResponse summary(UUID userId) {
        return buildSummary(userId, true);
    }

    /**
     * Somebody else's passport, as their profile shows it: the proofs they earned and
     * nothing that is theirs alone. The point balance and the progress towards the next
     * stamp stay out, and the wallet is never touched, so reading a stranger's passport
     * cannot create a wallet row for them.
     */
    @Override
    @Transactional(readOnly = true)
    public PassportSummaryResponse publicSummary(UUID userId) {
        return buildSummary(userId, false);
    }

    private PassportSummaryResponse buildSummary(UUID userId, boolean includePrivate) {
        Map<String, Object> counters = passportMapper.summarizeUser(userId);
        double locationRadiusKm = config.getDecimal(BusinessConfigKey.PASSPORT_LOCATION_PLACE_RADIUS_KM);
        List<Map<String, Object>> locationImageRows = passportMapper.findPassportLocationImageMap(
                userId, locationRadiusKm);
        int totalLocationImages = locationImageRows.size();
        int visitedLocationImages = (int) locationImageRows.stream()
                .filter(row -> asLong(row.get("event_count")) > 0)
                .count();
        int visitedProvinces = passportMapper.findVisitedProvinceCodes(userId).size();
        int totalProvinces = (int) passportMapper.countProvinces();
        if (totalProvinces == 0) {
            totalProvinces = config.getInt(BusinessConfigKey.PASSPORT_TOTAL_PROVINCES);
        }

        List<PassportStampRule> rules = passportMapper.findActiveRules();
        Set<String> earned = new HashSet<>();
        List<PassportStampResponse> stamps = passportMapper.findStampsByUser(userId).stream()
                .peek(stamp -> earned.add(stamp.getRuleCode() + ":" + stamp.getRuleVersion()))
                .map(stamp -> toStampResponse(stamp, rules))
                .toList();

        return PassportSummaryResponse.builder()
                .passportEnabled(config.getBoolean(BusinessConfigKey.PASSPORT_ENABLED))
                .checkinCount(asLong(counters.get("event_count")))
                .verifiedCheckinCount(asLong(counters.get("verified_event_count")))
                .distinctPlaceCount(asLong(counters.get("distinct_place_count")))
                .visitedProvinceCount(visitedProvinces)
                .totalProvinceCount(totalProvinces)
                .visitedLocationImageCount(visitedLocationImages)
                .totalLocationImageCount(totalLocationImages)
                .locationCompletionPercent(locationCompletionPercent(visitedLocationImages, totalLocationImages))
                .completionPercent(completionPercent(visitedProvinces, totalProvinces))
                .pointsBalance(includePrivate ? pointWallet.getWallet(userId).getBalance() : 0)
                .stamps(stamps)
                .nextStamps(includePrivate
                        ? progressTowardsNextStamps(rules, earned, counters, visitedProvinces)
                        : List.of())
                .earnedTags(passportMapper.findEarnedTagsByUser(userId).stream()
                        .map(this::toTagResponse)
                        .toList())
                .firstEventAt(asDateTime(counters.get("first_event_at")))
                .lastEventAt(asDateTime(counters.get("last_event_at")))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceMapEntryResponse> provinceMap(UUID userId) {
        requireFeatureEnabled();
        Set<String> visited = new HashSet<>(passportMapper.findVisitedProvinceCodes(userId));
        Set<String> wished = new HashSet<>(passportMapper.findWishedProvinceCodes(userId));

        Map<String, Integer> counts = new HashMap<>();
        for (Map<String, Object> row : passportMapper.countEventsByProvince(userId)) {
            Object code = row.get("code");
            Object total = row.get("total");
            if (code != null && total instanceof Number number) {
                counts.put(code.toString(), number.intValue());
            }
        }

        return passportMapper.findProvinces(null).stream()
                .map(province -> ProvinceMapEntryResponse.builder()
                        .code(province.getCode())
                        .name(province.getName())
                        .region(province.getRegion())
                        // Carried so the map can place a province without a second
                        // lookup, and so the app never hard-codes coordinates that
                        // would drift from the official list.
                        .latitude(province.getLatitude())
                        .longitude(province.getLongitude())
                        .visited(visited.contains(province.getCode()))
                        // Kept as its own field, never folded into "visited".
                        .wished(wished.contains(province.getCode()))
                        .eventCount(counts.getOrDefault(province.getCode(), 0))
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportLocationMapEntryResponse> locationImageMap(UUID userId) {
        requireFeatureEnabled();
        double radiusKm = config.getDecimal(BusinessConfigKey.PASSPORT_LOCATION_PLACE_RADIUS_KM);
        return passportMapper.findPassportLocationImageMap(userId, radiusKm).stream()
                .map(row -> PassportLocationMapEntryResponse.builder()
                        .id(asUuid(row.get("id")))
                        .name(asString(row.get("name")))
                        .address(asString(row.get("address")))
                        .imageUrl(asString(row.get("image_url")))
                        .latitude(asBigDecimal(row.get("latitude")))
                        .longitude(asBigDecimal(row.get("longitude")))
                        .visited(asLong(row.get("event_count")) > 0)
                        .eventCount((int) asLong(row.get("event_count")))
                        .build())
                // Keep configured anchors without coordinates in the response so the
                // client can list them and explain why the map is unavailable; admins can
                // then add coordinates instead of silently losing the Passport item.
                .filter(entry -> entry.getId() != null)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportEvent> timeline(UUID userId, int page, int size) {
        requireFeatureEnabled();
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // Every visit shows separately: three trips to one place are three memories, and
        // collapsing them would quietly delete two of them from somebody's history.
        return passportMapper.findEventsByUser(userId, true, safeSize, Math.max(0, page) * safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportEvent> province(UUID userId, String provinceCode, int limit) {
        requireFeatureEnabled();
        return passportMapper.findEventsByProvince(userId, provinceCode,
                Math.max(1, Math.min(limit, MAX_PAGE_SIZE)));
    }

    @Override
    @Transactional
    public void setEventHidden(UUID userId, UUID eventId, boolean hidden) {
        requireFeatureEnabled();
        if (passportMapper.setEventHidden(eventId, userId, hidden) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Passport event not found");
        }
    }

    @Override
    @Transactional
    public void addProvinceWish(UUID userId, String provinceCode) {
        requireFeatureEnabled();
        requireProvince(provinceCode);
        passportMapper.addWish(userId, provinceCode);
    }

    @Override
    @Transactional
    public void removeProvinceWish(UUID userId, String provinceCode) {
        requireFeatureEnabled();
        passportMapper.removeWish(userId, provinceCode);
    }

    // --- configured tag awards ------------------------------------------------------

    /**
     * Tags are evaluated from the event stream rather than the request payload. A tag
     * with several Places is an "all Places" challenge: requiredCheckinCount is the
     * minimum number of visits at each configured Place, not a pooled total. The unique
     * key on user_passport_tags is the final idempotency boundary if two async
     * consequence workers race for the same check-in.
     */
    private void evaluateTags(UUID userId, PassportEvent event) {
        for (PassportTag tag : passportMapper.findActiveQualifyingTags(
                event.getPlaceId(), event.getProvinceCode(), event.getLatitude(), event.getLongitude(),
                config.getDecimal(BusinessConfigKey.PASSPORT_LOCATION_PLACE_RADIUS_KM))) {
            int required = tag.getRequiredCheckinCount() == null ? 1 : tag.getRequiredCheckinCount();
            boolean hasCuratedPlaces = passportMapper.countTagPlaces(tag.getId()) > 0;
            if (hasCuratedPlaces) {
                // Every configured Place is a required stop. Repeating one Place cannot
                // compensate for a missing Place elsewhere in the tag.
                long qualifiedPlaces = passportMapper.countQualifiedTagPlaces(userId, tag);
                long totalPlaces = passportMapper.countTagPlaces(tag.getId());
                if (qualifiedPlaces < totalPlaces) {
                    continue;
                }
            } else if ("PASSPORT_LOCATIONS".equals(tag.getQualificationMode())
                    ? passportMapper.countQualifiedLocationTagLocations(userId, tag,
                    config.getDecimal(BusinessConfigKey.PASSPORT_LOCATION_PLACE_RADIUS_KM)) < required
                    : passportMapper.countQualifiedProvinceTagLocations(userId, tag) < required) {
                // A tag without explicit Places is a scope challenge. Count
                // distinct catalogued or coordinate clusters, so repeated check-ins at
                // one restaurant do not fake exploration of the configured scope.
                continue;
            }
            passportMapper.insertUserTag(UserPassportTag.builder()
                    .userId(userId)
                    .passportTagId(tag.getId())
                    .triggeringEventId(event.getId())
                    .awardedAt(LocalDateTime.now())
                    .build());
        }
    }

    private void projectExistingTagAwards(PassportTag tag) {
        // An inactive tag (or its inactive Passport collection) must never create a
        // new award while an administrator is editing the catalogue. Existing awards
        // remain immutable history and are still rendered to the user.
        if (!Boolean.TRUE.equals(tag.getIsActive())) {
            return;
        }
        PassportDefinition definition = passportMapper.findPassportDefinition(tag.getPassportId());
        if (definition == null || !Boolean.TRUE.equals(definition.getIsActive())) {
            return;
        }
        passportMapper.findTagAwardCandidates(tag,
                config.getDecimal(BusinessConfigKey.PASSPORT_LOCATION_PLACE_RADIUS_KM)).forEach(row -> {
            UUID userId = asUuid(row.get("user_id"));
            if (userId == null) {
                return;
            }
            passportMapper.insertUserTag(UserPassportTag.builder()
                    .userId(userId)
                    .passportTagId(tag.getId())
                    .triggeringEventId(asUuid(row.get("triggering_event_id")))
                    .awardedAt(LocalDateTime.now())
                    .build());
        });
    }

    /**
     * Resolves only unambiguous data. The order is intentionally reliable-to-fallback:
     * catalogue place, supplied official code, then an exact official alias in the
     * administrative/address text. Raw map locations remain eligible when their address
     * identifies a city, without pretending that an ambiguous coordinate is a province.
     */
    private String resolveProvinceCode(UserCheckin checkin) {
        if (checkin.getPlaceId() != null) {
            String placeCode = passportMapper.findPlaceProvinceCode(checkin.getPlaceId());
            if (isKnownProvinceCode(placeCode)) {
                return placeCode;
            }
        }
        if (isKnownProvinceCode(checkin.getProvinceCode())) {
            return checkin.getProvinceCode();
        }

        List<String> candidates = List.of(
                checkin.getProvince() == null ? "" : checkin.getProvince(),
                checkin.getLocationName() == null ? "" : checkin.getLocationName());
        for (String candidate : candidates) {
            String resolved = resolveProvinceFromText(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private boolean isKnownProvinceCode(String code) {
        return code != null && !code.isBlank() && passportMapper.findProvinceByCode(code.trim()) != null;
    }

    private String resolveProvinceFromText(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return null;
        }
        List<ProvinceAliasMatch> matches = new ArrayList<>();
        for (Province province : passportMapper.findProvinces(null)) {
            for (String alias : provinceAliases(province)) {
                if (containsWholePhrase(normalized, alias)) {
                    matches.add(new ProvinceAliasMatch(province.getCode(), alias.length()));
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        int longest = matches.stream().mapToInt(ProvinceAliasMatch::length).max().orElse(0);
        Set<String> codes = matches.stream()
                .filter(match -> match.length() == longest)
                .map(ProvinceAliasMatch::code)
                .collect(java.util.stream.Collectors.toSet());
        return codes.size() == 1 ? codes.iterator().next() : null;
    }

    private List<String> provinceAliases(Province province) {
        List<String> aliases = JsonUtils.fromJson(
                province.getAliases(), new TypeReference<List<String>>() { });
        List<String> values = new ArrayList<>();
        values.add(normalize(province.getNormalizedName()));
        if (aliases != null) {
            aliases.stream().map(this::normalize).filter(alias -> !alias.isBlank()).forEach(values::add);
        }
        return values.stream().distinct().toList();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase()
                .replaceAll("[^a-z0-9]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsWholePhrase(String haystack, String phrase) {
        String padded = " " + haystack + " ";
        return padded.contains(" " + phrase + " ");
    }

    private record ProvinceAliasMatch(String code, int length) {
    }

    // --- operator catalogue ---------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public List<PassportDefinitionResponse> passportDefinitions(boolean includeInactive) {
        return passportMapper.findPassportDefinitions(includeInactive).stream()
                .map(this::toDefinitionResponse)
                .toList();
    }

    @Override
    @Transactional
    public PassportDefinitionResponse createPassportDefinition(UpsertPassportDefinitionRequest request) {
        List<UUID> locationImageIds = validateLocationImageIds(request.getLocationImageIds());
        List<String> provinceCodes = validateLegacyProvinceCodes(request.getProvinceCodes());
        LocalDateTime now = LocalDateTime.now();
        PassportDefinition definition = PassportDefinition.builder()
                .id(UUID.randomUUID())
                .code(normalizeCode(request.getCode()))
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .coverImageUrl(blankToNull(request.getCoverImageUrl()))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .displayOrder(defaultInt(request.getDisplayOrder(), 0))
                .locationImageIds(locationImageIds)
                .provinceCodes(provinceCodes)
                .createdAt(now)
                .updatedAt(now)
                .build();
        passportMapper.insertPassportDefinition(definition);
        replacePassportLocationImages(definition.getId(), locationImageIds);
        replacePassportProvinces(definition.getId(), provinceCodes);
        return toDefinitionResponse(definition);
    }

    @Override
    @Transactional
    public PassportDefinitionResponse updatePassportDefinition(UUID id, UpsertPassportDefinitionRequest request) {
        PassportDefinition existing = requireDefinition(id);
        List<UUID> requestedLocationImageIds = distinctIds(request.getLocationImageIds());
        List<UUID> locationImageIds = requestedLocationImageIds.isEmpty()
                ? passportMapper.findPassportLocationImageIds(id) : validateLocationImageIds(requestedLocationImageIds);
        List<String> provinceCodes = validateLegacyProvinceCodes(request.getProvinceCodes());
        if (locationImageIds.isEmpty() && provinceCodes.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Passport cần gắn với ít nhất một Location Image");
        }
        existing.setCode(normalizeCode(request.getCode()));
        existing.setName(request.getName().trim());
        existing.setDescription(blankToNull(request.getDescription()));
        existing.setCoverImageUrl(blankToNull(request.getCoverImageUrl()));
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());
        existing.setDisplayOrder(defaultInt(request.getDisplayOrder(), 0));
        existing.setLocationImageIds(locationImageIds);
        existing.setProvinceCodes(provinceCodes);
        existing.setUpdatedAt(LocalDateTime.now());
        passportMapper.updatePassportDefinition(existing);
        replacePassportLocationImages(existing.getId(), locationImageIds);
        replacePassportProvinces(existing.getId(), provinceCodes);
        return toDefinitionResponse(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportProvinceOptionResponse> provinceOptions() {
        return passportMapper.findProvinces(null).stream()
                .map(province -> PassportProvinceOptionResponse.builder()
                        .code(province.getCode())
                        .name(province.getName())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportTagResponse> passportTags(boolean includeInactive) {
        return passportMapper.findPassportTags(includeInactive).stream()
                .map(this::toTagResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PlacePassportTagsResponse placePassportTags(UUID userId, UUID placeId) {
        requireFeatureEnabled();
        if (passportMapper.countExistingPlaces(List.of(placeId)) != 1) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "Place not found");
        }
        return PlacePassportTagsResponse.builder()
                .tags(passportMapper.findPassportTagsForPlace(userId, placeId,
                                config.getDecimal(BusinessConfigKey.PASSPORT_LOCATION_PLACE_RADIUS_KM)).stream()
                        .map(this::toTagResponse)
                        .toList())
                .checkinCount((int) passportMapper.countUserCheckinsAtPlace(userId, placeId))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportStampRuleResponse> passportStampRules(boolean includeInactive) {
        return passportMapper.findStampRules(includeInactive).stream()
                .map(this::toStampRuleResponse)
                .toList();
    }

    @Override
    @Transactional
    public PassportStampRuleResponse createPassportStampRule(UpsertPassportStampRuleRequest request) {
        PassportStampRule rule = PassportStampRule.builder()
                .code(normalizeCode(request.getCode()))
                .version(defaultInt(request.getVersion(), 1))
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .conditionType(request.getConditionType().trim().toUpperCase())
                .threshold(defaultInt(request.getThreshold(), 1))
                .icon(blankToNull(request.getIcon()))
                .rewardPoints(defaultInt(request.getRewardPoints(), 0))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .createdAt(LocalDateTime.now())
                .build();
        passportMapper.insertStampRule(rule);
        return toStampRuleResponse(rule);
    }

    @Override
    @Transactional
    public PassportStampRuleResponse updatePassportStampRule(
            String code, int version, UpsertPassportStampRuleRequest request) {
        PassportStampRule existing = passportMapper.findRule(normalizeCode(code), version);
        if (existing == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Passport stamp rule not found");
        }
        existing.setName(request.getName().trim());
        existing.setDescription(blankToNull(request.getDescription()));
        existing.setConditionType(request.getConditionType().trim().toUpperCase());
        existing.setThreshold(defaultInt(request.getThreshold(), 1));
        existing.setIcon(blankToNull(request.getIcon()));
        existing.setRewardPoints(defaultInt(request.getRewardPoints(), 0));
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());
        passportMapper.updateStampRule(existing);
        return toStampRuleResponse(existing);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportRewardResponse> passportRewards(boolean includeInactive) {
        return passportMapper.findPassportRewards(includeInactive).stream()
                .map(this::toRewardResponse)
                .toList();
    }

    @Override
    @Transactional
    public PassportRewardResponse createPassportReward(UpsertPassportRewardRequest request) {
        LocalDateTime now = LocalDateTime.now();
        PassportReward reward = PassportReward.builder()
                .id(UUID.randomUUID())
                .code(normalizeCode(request.getCode()))
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .pointsCost(defaultInt(request.getPointsCost(), 0))
                .requiredStampCode(blankToNull(request.getRequiredStampCode()))
                .totalQuantity(request.getTotalQuantity())
                .issuedQuantity(0)
                .validDays(defaultInt(request.getValidDays(), 30))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .createdAt(now)
                .updatedAt(now)
                .build();
        passportMapper.insertPassportReward(reward);
        return toRewardResponse(reward);
    }

    @Override
    @Transactional
    public PassportRewardResponse updatePassportReward(UUID id, UpsertPassportRewardRequest request) {
        PassportReward existing = passportMapper.findPassportReward(id);
        if (existing == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Passport reward not found");
        }
        Integer totalQuantity = request.getTotalQuantity();
        if (totalQuantity != null && totalQuantity < defaultInt(existing.getIssuedQuantity(), 0)) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Total quantity cannot be lower than issued quantity");
        }
        existing.setCode(normalizeCode(request.getCode()));
        existing.setName(request.getName().trim());
        existing.setDescription(blankToNull(request.getDescription()));
        existing.setPointsCost(defaultInt(request.getPointsCost(), 0));
        existing.setRequiredStampCode(blankToNull(request.getRequiredStampCode()));
        existing.setTotalQuantity(totalQuantity);
        existing.setValidDays(defaultInt(request.getValidDays(), 30));
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());
        existing.setUpdatedAt(LocalDateTime.now());
        passportMapper.updatePassportReward(existing);
        return toRewardResponse(existing);
    }

    @Override
    @Transactional
    public PassportTagResponse createPassportTag(UpsertPassportTagRequest request) {
        validateTagRequest(request);
        List<UUID> placeIds = distinctIds(request.getPlaceIds());
        LocalDateTime now = LocalDateTime.now();
        PassportTag tag = PassportTag.builder()
                .id(UUID.randomUUID())
                .passportId(request.getPassportId())
                .code(normalizeCode(request.getCode()))
                .name(request.getName().trim())
                .description(blankToNull(request.getDescription()))
                .imageUrl(blankToNull(request.getImageUrl()))
                .qualificationMode(resolveTagQualificationMode(request, placeIds))
                .requiredCheckinCount(defaultInt(request.getRequiredCheckinCount(), 1))
                .isActive(request.getIsActive() == null || request.getIsActive())
                .displayOrder(defaultInt(request.getDisplayOrder(), 0))
                .createdAt(now)
                .updatedAt(now)
                .build();
        passportMapper.insertPassportTag(tag);
        replaceTagReferences(tag.getId(), request);
        PassportTag saved = requireTag(tag.getId());
        projectExistingTagAwards(saved);
        return toTagResponse(saved);
    }

    @Override
    @Transactional
    public PassportTagResponse updatePassportTag(UUID id, UpsertPassportTagRequest request) {
        validateTagRequest(request);
        PassportTag existing = requireTag(id);
        existing.setPassportId(request.getPassportId());
        existing.setCode(normalizeCode(request.getCode()));
        existing.setName(request.getName().trim());
        existing.setDescription(blankToNull(request.getDescription()));
        existing.setImageUrl(blankToNull(request.getImageUrl()));
        existing.setQualificationMode(resolveTagQualificationMode(request, distinctIds(request.getPlaceIds())));
        existing.setRequiredCheckinCount(defaultInt(request.getRequiredCheckinCount(), 1));
        existing.setIsActive(request.getIsActive() == null || request.getIsActive());
        existing.setDisplayOrder(defaultInt(request.getDisplayOrder(), 0));
        existing.setUpdatedAt(LocalDateTime.now());
        passportMapper.updatePassportTag(existing);
        replaceTagReferences(id, request);
        PassportTag saved = requireTag(id);
        projectExistingTagAwards(saved);
        return toTagResponse(saved);
    }

    @Override
    public String uploadCatalogImage(MultipartFile file) {
        ImageUploadOutcome outcome = fileUploadService.uploadImage(ImageUploadRequest.of(
                null, ImageUploadRequest.ImageEntryPoint.PASSPORT_CATALOG, "passport-catalog"), file);
        if (!outcome.isAccepted()) {
            throw new BusinessException(
                    outcome.isContentRejection() ? ErrorConstant.IMAGE_REJECTED_BY_MODERATION
                            : ErrorConstant.INVALID_PARAMETERS,
                    outcome.failureMessage());
        }
        return outcome.url();
    }

    private void validateTagRequest(UpsertPassportTagRequest request) {
        requireDefinition(request.getPassportId());
        List<UUID> placeIds = distinctIds(request.getPlaceIds());
        String mode = resolveTagQualificationMode(request, placeIds);
        if ("SPECIFIC_PLACES".equals(mode) && placeIds.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Choose at least one Place or select a Passport scope for this tag");
        }
        if (("PASSPORT_PROVINCES".equals(mode) || "PASSPORT_LOCATIONS".equals(mode)) && !placeIds.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "A Passport-scope tag cannot include explicit Places");
        }
        if ("PASSPORT_LOCATIONS".equals(mode)) {
            PassportDefinition definition = requireDefinition(request.getPassportId());
            List<UUID> locationImageIds = definition.getLocationImageIds() == null
                    ? passportMapper.findPassportLocationImageIds(definition.getId()) : definition.getLocationImageIds();
            if (locationImageIds == null || locationImageIds.isEmpty()) {
                throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                        "Passport cần có Location Image trước khi dùng scope mặc định");
            }
        }
        if (!placeIds.isEmpty() && passportMapper.countExistingPlaces(placeIds) != placeIds.size()) {
            throw new BusinessException(ErrorConstant.PLACE_NOT_FOUND, "One or more places were not found");
        }
    }

    private String resolveTagQualificationMode(UpsertPassportTagRequest request, List<UUID> placeIds) {
        String requested = request.getQualificationMode();
        if (requested != null && !requested.isBlank()) {
            String normalized = requested.trim().toUpperCase();
            if ("PASSPORT_PROVINCES".equals(normalized)
                    || "PASSPORT_LOCATIONS".equals(normalized)
                    || "SPECIFIC_PLACES".equals(normalized)) {
                return normalized;
            }
        }
        return placeIds.isEmpty() ? "PASSPORT_LOCATIONS" : "SPECIFIC_PLACES";
    }

    private void replaceTagReferences(UUID tagId, UpsertPassportTagRequest request) {
        passportMapper.deleteTagPlaces(tagId);
        distinctIds(request.getPlaceIds())
                .forEach(placeId -> passportMapper.insertTagPlace(tagId, placeId));
    }

    private List<UUID> distinctIds(Collection<UUID> values) {
        return values == null ? List.of() : values.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private PassportDefinition requireDefinition(UUID id) {
        PassportDefinition definition = passportMapper.findPassportDefinition(id);
        if (definition == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Passport definition not found");
        }
        return definition;
    }

    private PassportTag requireTag(UUID id) {
        PassportTag tag = passportMapper.findPassportTag(id);
        if (tag == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Passport tag not found");
        }
        return tag;
    }

    private PassportDefinitionResponse toDefinitionResponse(PassportDefinition definition) {
        return PassportDefinitionResponse.builder()
                .id(definition.getId())
                .code(definition.getCode())
                .name(definition.getName())
                .description(definition.getDescription())
                .coverImageUrl(definition.getCoverImageUrl())
                .isActive(Boolean.TRUE.equals(definition.getIsActive()))
                .displayOrder(defaultInt(definition.getDisplayOrder(), 0))
                .locationImageIds(definition.getLocationImageIds() == null
                        ? passportMapper.findPassportLocationImageIds(definition.getId())
                        : definition.getLocationImageIds())
                .provinceCodes(definition.getProvinceCodes() == null
                        ? passportMapper.findPassportProvinceCodes(definition.getId())
                        : definition.getProvinceCodes())
                .createdAt(definition.getCreatedAt())
                .updatedAt(definition.getUpdatedAt())
                .build();
    }

    private PassportTagResponse toTagResponse(PassportTag tag) {
        return PassportTagResponse.builder()
                .id(tag.getId())
                .passportId(tag.getPassportId())
                .passportCode(tag.getPassportCode())
                .passportName(tag.getPassportName())
                .code(tag.getCode())
                .name(tag.getName())
                .description(tag.getDescription())
                .imageUrl(tag.getImageUrl())
                .qualificationMode(tag.getQualificationMode())
                .requiredCheckinCount(defaultInt(tag.getRequiredCheckinCount(), 1))
                .isActive(Boolean.TRUE.equals(tag.getIsActive()))
                .displayOrder(defaultInt(tag.getDisplayOrder(), 0))
                .placeIds(passportMapper.findTagPlaceIds(tag.getId()))
                .earnedAt(tag.getEarnedAt())
                .triggeringEventId(tag.getTriggeringEventId())
                .createdAt(tag.getCreatedAt())
                .updatedAt(tag.getUpdatedAt())
                .build();
    }

    private List<UUID> validateLocationImageIds(Collection<UUID> values) {
        List<UUID> ids = distinctIds(values);
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Passport cần gắn với ít nhất một Location Image");
        }
        if (passportMapper.countExistingLocationImages(ids) != ids.size()) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "One or more Location Images were not found");
        }
        if (passportMapper.countLocationImagesWithCoordinates(ids) != ids.size()) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS,
                    "Location Image phải có tọa độ trước khi gắn vào Passport");
        }
        return ids;
    }

    private List<String> validateLegacyProvinceCodes(Collection<String> values) {
        List<String> codes = values == null ? List.of() : values.stream()
                .filter(java.util.Objects::nonNull)
                .map(this::normalizeProvinceCode)
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
        codes.forEach(this::requireProvince);
        return codes;
    }

    private void replacePassportLocationImages(UUID passportId, List<UUID> locationImageIds) {
        passportMapper.deletePassportLocationImages(passportId);
        locationImageIds.forEach(id -> passportMapper.insertPassportLocationImage(passportId, id));
    }

    private void replacePassportProvinces(UUID passportId, List<String> provinceCodes) {
        passportMapper.deletePassportProvinces(passportId);
        provinceCodes.forEach(code -> passportMapper.insertPassportProvince(passportId, code));
    }

    private String normalizeProvinceCode(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("^_+|_+$", "");
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int defaultInt(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    // --- stamps ----------------------------------------------------------------------

    /**
     * Re-evaluates every active rule after a new event.
     *
     * <p>Driven by the event rather than by a periodic sweep: a sweep costs more and is the
     * usual way a rule ends up granting twice. Granting is idempotent at the database
     * level, so running this again changes nothing.
     */
    private void evaluateStamps(UUID userId, UUID triggeringEventId) {
        Map<String, Object> counters = passportMapper.summarizeUser(userId);
        int visitedProvinces = passportMapper.findVisitedProvinceCodes(userId).size();

        for (PassportStampRule rule : passportMapper.findActiveRules()) {
            if (!isSatisfied(rule, counters, visitedProvinces)) {
                continue;
            }
            PassportStamp stamp = PassportStamp.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .ruleCode(rule.getCode())
                    .ruleVersion(rule.getVersion())
                    .triggeringEventId(triggeringEventId)
                    .awardedAt(LocalDateTime.now())
                    .build();
            if (passportMapper.insertStamp(stamp) > 0 && rule.getRewardPoints() != null
                    && rule.getRewardPoints() > 0) {
                pointWallet.grant(userId, rule.getRewardPoints(), "PASSPORT_STAMP",
                        "stamp:" + userId + ":" + rule.getCode() + ":" + rule.getVersion(),
                        rule.getName());
            }
        }
    }

    private boolean isSatisfied(PassportStampRule rule, Map<String, Object> counters, int visitedProvinces) {
        int threshold = rule.getThreshold() == null ? 1 : rule.getThreshold();
        return currentValue(rule, counters, visitedProvinces) >= threshold;
    }

    private int currentValue(PassportStampRule rule, Map<String, Object> counters, int visitedProvinces) {
        return switch (rule.getConditionType()) {
            case "FIRST_CHECKIN", "CHECKIN_COUNT" -> (int) asLong(counters.get("event_count"));
            case "VERIFIED_CHECKIN_COUNT" -> (int) asLong(counters.get("verified_event_count"));
            case "DISTINCT_PLACE_COUNT" -> (int) asLong(counters.get("distinct_place_count"));
            case "DISTINCT_PROVINCE_COUNT" -> visitedProvinces;
            default -> 0;
        };
    }

    private List<PassportStampProgressResponse> progressTowardsNextStamps(
            List<PassportStampRule> rules, Set<String> earned, Map<String, Object> counters,
            int visitedProvinces) {
        return rules.stream()
                .filter(rule -> !earned.contains(rule.getCode() + ":" + rule.getVersion()))
                .map(rule -> PassportStampProgressResponse.builder()
                        .code(rule.getCode())
                        .name(rule.getName())
                        .description(rule.getDescription())
                        .icon(rule.getIcon())
                        .current(currentValue(rule, counters, visitedProvinces))
                        .threshold(rule.getThreshold() == null ? 1 : rule.getThreshold())
                        // Only a countable condition gets a progress bar; a yes/no rule
                        // shows its description instead of a meaningless 0 of 1.
                        .countable(!"FIRST_CHECKIN".equals(rule.getConditionType()))
                        .build())
                .toList();
    }

    private Double locationCompletionPercent(int visitedLocations, int totalLocations) {
        if (totalLocations <= 0) {
            return null;
        }
        return BigDecimal.valueOf((double) visitedLocations * 100 / totalLocations)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Withheld until the province dataset is complete enough. Publishing a percentage
     * computed from partial data invites the user to conclude their own history is wrong.
     */
    private Double completionPercent(int visitedProvinces, int totalProvinces) {
        Map<String, Object> coverage = passportMapper.provinceCoverage();
        long withCoordinates = asLong(coverage.get("places_with_coordinates"));
        long assigned = asLong(coverage.get("assigned_places_with_coordinates"));
        if (withCoordinates == 0 || totalProvinces == 0) {
            return null;
        }
        double coveragePercent = (double) assigned * 100 / withCoordinates;
        if (coveragePercent < config.getInt(BusinessConfigKey.PASSPORT_PROVINCE_COVERAGE_THRESHOLD)) {
            return null;
        }
        return BigDecimal.valueOf((double) visitedProvinces * 100 / totalProvinces)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private PassportStampResponse toStampResponse(PassportStamp stamp, List<PassportStampRule> rules) {
        PassportStampRule rule = rules.stream()
                .filter(candidate -> candidate.getCode().equals(stamp.getRuleCode())
                        && candidate.getVersion().equals(stamp.getRuleVersion()))
                .findFirst()
                // A stamp granted under a rule version that is no longer active still has
                // to render; the version it was earned under is what explains it.
                .orElseGet(() -> passportMapper.findRule(stamp.getRuleCode(), stamp.getRuleVersion()));

        return PassportStampResponse.builder()
                .code(stamp.getRuleCode())
                .version(stamp.getRuleVersion())
                .name(rule == null ? stamp.getRuleCode() : rule.getName())
                .description(rule == null ? null : rule.getDescription())
                .icon(rule == null ? null : rule.getIcon())
                .awardedAt(stamp.getAwardedAt())
                .triggeringEventId(stamp.getTriggeringEventId())
                .build();
    }

    private boolean isPassportEnabled() {
        return config.getBoolean(BusinessConfigKey.PASSPORT_ENABLED);
    }

    private PassportStampRuleResponse toStampRuleResponse(PassportStampRule rule) {
        return PassportStampRuleResponse.builder()
                .code(rule.getCode())
                .version(defaultInt(rule.getVersion(), 1))
                .name(rule.getName())
                .description(rule.getDescription())
                .conditionType(rule.getConditionType())
                .threshold(defaultInt(rule.getThreshold(), 1))
                .icon(rule.getIcon())
                .rewardPoints(defaultInt(rule.getRewardPoints(), 0))
                .isActive(Boolean.TRUE.equals(rule.getIsActive()))
                .createdAt(rule.getCreatedAt())
                .build();
    }

    private PassportRewardResponse toRewardResponse(PassportReward reward) {
        return PassportRewardResponse.builder()
                .id(reward.getId())
                .code(reward.getCode())
                .name(reward.getName())
                .description(reward.getDescription())
                .pointsCost(defaultInt(reward.getPointsCost(), 0))
                .requiredStampCode(reward.getRequiredStampCode())
                .totalQuantity(reward.getTotalQuantity())
                .issuedQuantity(defaultInt(reward.getIssuedQuantity(), 0))
                .validDays(defaultInt(reward.getValidDays(), 30))
                .isActive(Boolean.TRUE.equals(reward.getIsActive()))
                .createdAt(reward.getCreatedAt())
                .updatedAt(reward.getUpdatedAt())
                .build();
    }

    private void requireFeatureEnabled() {
        if (!isPassportEnabled()) {
            throw new BusinessException(ErrorConstant.PASSPORT_FEATURE_DISABLED,
                    "Passport is currently unavailable.");
        }
    }

    private void requireProvince(String provinceCode) {
        Province province = passportMapper.findProvinceByCode(provinceCode);
        if (province == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Province not found");
        }
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private UUID asUuid(Object value) {
        if (value instanceof UUID id) {
            return id;
        }
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value.toString());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private LocalDateTime asDateTime(Object value) {
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }
}
