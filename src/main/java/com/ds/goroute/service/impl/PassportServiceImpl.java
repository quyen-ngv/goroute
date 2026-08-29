package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.response.PassportStampProgressResponse;
import com.ds.goroute.dto.response.PassportStampResponse;
import com.ds.goroute.dto.response.PassportSummaryResponse;
import com.ds.goroute.dto.response.ProvinceMapEntryResponse;
import com.ds.goroute.entity.PassportEvent;
import com.ds.goroute.entity.PassportStamp;
import com.ds.goroute.entity.PassportStampRule;
import com.ds.goroute.entity.Province;
import com.ds.goroute.entity.UserCheckin;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.PassportMapper;
import com.ds.goroute.service.BusinessConfigService;
import com.ds.goroute.service.PassportService;
import com.ds.goroute.service.StarService;
import com.ds.goroute.type.BusinessConfigKey;
import com.ds.goroute.type.CheckinVerificationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
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

    @Override
    @Transactional
    public void recordCheckin(UserCheckin checkin) {
        PassportEvent event = PassportEvent.builder()
                .id(UUID.randomUUID())
                .userId(checkin.getUserId())
                .source(SOURCE_USER_CHECKIN)
                .sourceId(checkin.getId())
                .placeId(checkin.getPlaceId())
                .checkinId(checkin.getId())
                .locationKey(checkin.getLocationKey())
                .locationName(checkin.getLocationName())
                .provinceCode(checkin.getProvinceCode())
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
            }
        }
        touchedUsers.forEach(userId -> evaluateStamps(userId, null));
        return created;
    }

    @Override
    @Transactional(readOnly = true)
    public PassportSummaryResponse summary(UUID userId) {
        Map<String, Object> counters = passportMapper.summarizeUser(userId);
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
                .completionPercent(completionPercent(visitedProvinces, totalProvinces))
                .pointsBalance(pointWallet.getWallet(userId).getBalance())
                .stamps(stamps)
                .nextStamps(progressTowardsNextStamps(rules, earned, counters, visitedProvinces))
                .firstEventAt(asDateTime(counters.get("first_event_at")))
                .lastEventAt(asDateTime(counters.get("last_event_at")))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProvinceMapEntryResponse> provinceMap(UUID userId) {
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
    public List<PassportEvent> timeline(UUID userId, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        // Every visit shows separately: three trips to one place are three memories, and
        // collapsing them would quietly delete two of them from somebody's history.
        return passportMapper.findEventsByUser(userId, true, safeSize, Math.max(0, page) * safeSize);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PassportEvent> province(UUID userId, String provinceCode, int limit) {
        return passportMapper.findEventsByProvince(userId, provinceCode,
                Math.max(1, Math.min(limit, MAX_PAGE_SIZE)));
    }

    @Override
    @Transactional
    public void setEventHidden(UUID userId, UUID eventId, boolean hidden) {
        if (passportMapper.setEventHidden(eventId, userId, hidden) != 1) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Passport event not found");
        }
    }

    @Override
    @Transactional
    public void addProvinceWish(UUID userId, String provinceCode) {
        requireProvince(provinceCode);
        passportMapper.addWish(userId, provinceCode);
    }

    @Override
    @Transactional
    public void removeProvinceWish(UUID userId, String provinceCode) {
        passportMapper.removeWish(userId, provinceCode);
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

    private void requireProvince(String provinceCode) {
        Province province = passportMapper.findProvinceByCode(provinceCode);
        if (province == null) {
            throw new BusinessException(ErrorConstant.NOT_FOUND, "Province not found");
        }
    }

    private long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private LocalDateTime asDateTime(Object value) {
        return value instanceof LocalDateTime dateTime ? dateTime : null;
    }
}
