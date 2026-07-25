package com.ds.goroute.service.impl;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.request.AiTripConfirmRequest;
import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.request.CreateTripRequest;
import com.ds.goroute.dto.request.UpdateTripRequest;
import com.ds.goroute.dto.response.AiTripCandidateResponse;
import com.ds.goroute.dto.response.AiTripConfirmResponse;
import com.ds.goroute.dto.response.AiTripGenerateResponse;
import com.ds.goroute.dto.response.AiTripUsage;
import com.ds.goroute.dto.response.TripResponse;
import com.ds.goroute.entity.Activity;
import com.ds.goroute.entity.ActivityBooking;
import com.ds.goroute.entity.AiTripDraft;
import com.ds.goroute.entity.Place;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.mapper.ActivityBookingGeoSearchParams;
import com.ds.goroute.repository.ActivityBookingRepository;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AiTripRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.AiTripService;
import com.ds.goroute.service.TripService;
import com.ds.goroute.thirdparty.ai.AiClient;
import com.ds.goroute.type.ActivityStatus;
import com.ds.goroute.type.PlaceGroup;
import com.ds.goroute.type.TransportMode;
import com.ds.goroute.utils.AiTripGenerationSummary;
import com.ds.goroute.utils.AiTripLanguageSupport;
import com.ds.goroute.utils.DestinationMatchUtils;
import com.ds.goroute.utils.JsonUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiTripServiceImpl implements AiTripService {

    private static final int FREE_LIMIT = 3;
    private static final int PRO_LIMIT = 10;
    private static final double SEARCH_RADIUS_KM = 80.0;
    private static final String AI_TRIP_SYSTEM_CONTEXT = """
            This is a travel planning app for foreign tourists visiting Vietnam.
            Place names, addresses, and descriptions may be in Vietnamese - treat them as ground truth.
            """;

    private final AiTripRepository aiTripRepository;
    private final PlaceRepository placeRepository;
    private final ActivityBookingRepository activityBookingRepository;
    private final ActivityRepository activityRepository;
    private final TripService tripService;
    private final AiClient aiClient;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public AiTripUsage getEligibility(UUID userId) {
        aiTripRepository.ensureSubscription(userId);
        String tier = aiTripRepository.getSubscriptionTier(userId);
        int used = aiTripRepository.getAiTripsUsed(userId);
        int limit = limitForTier(tier);

        return AiTripUsage.builder()
                .tier(tier)
                .used(used)
                .limit(limit)
                .eligible(used < limit)
                .build();
    }

    @Override
    @Transactional
    public AiTripGenerateResponse generateCandidates(AiTripGenerateRequest request, UUID userId) {
        validateGenerateRequest(request);

        aiTripRepository.ensureSubscription(userId);
        if (aiTripRepository.consumeAiTripQuota(userId) == 0) {
            throw new BusinessException(ErrorConstant.AI_TRIP_QUOTA_EXHAUSTED);
        }

        String tier = aiTripRepository.getSubscriptionTier(userId);
        int used = aiTripRepository.getAiTripsUsed(userId);
        int limit = limitForTier(tier);

        List<PlaceGroup> groups = normalizeGroups(request.getPlaceGroups());
        List<AiTripCandidateResponse> candidates = collectCandidates(request, groups);
        candidates = rankCandidatesWithAi(request, candidates);

        UUID draftId = UUID.randomUUID();
        AiTripDraft draft = AiTripDraft.builder()
                .id(draftId)
                .userId(userId)
                .tripName(defaultTripName(request))
                .cityId(request.getCityId())
                .cityName(request.getCityName())
                .cityLat(request.getCityLat())
                .cityLng(request.getCityLng())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .dayCount(resolveDayCount(request))
                .placeGroups(JsonUtils.toJson(groups.stream().map(Enum::name).toList()))
                .pace(normalizePace(request.getPace()))
                .preferenceText(request.getPreferenceText())
                .groupComposition(request.getGroupComposition())
                .budgetMin(request.getBudgetMin())
                .budgetMax(request.getBudgetMax())
                .budgetCurrency(request.getBudgetCurrency())
                .travelStyle(request.getTravelStyle())
                .activityTypes(JsonUtils.toJson(request.getActivityTypes()))
                .dietaryRestrictions(JsonUtils.toJson(request.getDietaryRestrictions()))
                .mobilityConsiderations(JsonUtils.toJson(request.getMobilityConsiderations()))
                .includeBackupActivities(request.getIncludeBackupActivities())
                .candidates(JsonUtils.toJson(candidates))
                .status("PENDING")
                .build();
        aiTripRepository.insertDraft(draft);

        return AiTripGenerateResponse.builder()
                .draftId(draftId)
                .tier(tier)
                .aiTripsUsed(used)
                .aiTripLimit(limit)
                .candidates(candidates)
                .appliedGroupComposition(request.getGroupComposition())
                .appliedBudgetRange(buildBudgetRangeSummary(request))
                .appliedActivityTypes(request.getActivityTypes())
                .appliedDietaryRestrictions(request.getDietaryRestrictions())
                .backupActivitiesIncluded(request.getIncludeBackupActivities())
                .build();
    }

    private String buildBudgetRangeSummary(AiTripGenerateRequest request) {
        if (request.getBudgetMin() == null && request.getBudgetMax() == null) {
            return null;
        }
        String currency = request.getBudgetCurrency() != null ? request.getBudgetCurrency() : "VND";
        if (request.getBudgetMin() != null && request.getBudgetMax() != null) {
            return String.format("%s - %s %s/day", request.getBudgetMin(), request.getBudgetMax(), currency);
        } else if (request.getBudgetMax() != null) {
            return String.format("Up to %s %s/day", request.getBudgetMax(), currency);
        }
        return null;
    }

    @Override
    @Transactional
    public AiTripConfirmResponse confirmTrip(UUID draftId, AiTripConfirmRequest request, UUID userId) {
        AiTripDraft draft = aiTripRepository.findDraftForUpdate(draftId, userId)
                .orElseThrow(() -> new BusinessException(ErrorConstant.NOT_FOUND, "AI trip draft not found"));

        if ("COMPLETED".equals(draft.getStatus())) {
            return completedResponse(draft);
        }
        if (!"PENDING".equals(draft.getStatus())) {
            throw new BusinessException(ErrorConstant.AI_TRIP_DRAFT_INACTIVE);
        }

        List<AiTripCandidateResponse> candidates = parseCandidates(draft.getCandidates());
        Map<String, AiTripCandidateResponse> candidatesById = candidates.stream()
                .collect(Collectors.toMap(AiTripCandidateResponse::getId, Function.identity(), (a, b) -> a));

        List<String> selectedIds = request.getSelectedCandidateIds() == null
                ? List.of()
                : request.getSelectedCandidateIds().stream().distinct().toList();
        List<AiTripCandidateResponse> selected = selectedIds.stream()
                .map(candidatesById::get)
                .filter(Objects::nonNull)
                .toList();

        List<ScheduledCandidate> schedule = scheduleCandidatesWithAi(draft, selected);
        Set<String> scheduledIds = schedule.stream()
                .map(item -> item.candidate().getId())
                .collect(Collectors.toSet());
        List<AiTripCandidateResponse> skipped = selected.stream()
                .filter(candidate -> !scheduledIds.contains(candidate.getId()))
                .toList();

        Map<String, String> visitTips = generateVisitTips(draft, schedule);
        int filledDays = schedule.stream().map(ScheduledCandidate::dayNumber).max(Integer::compareTo).orElse(0);
        String generationSummary = buildGenerationSummary(draft, selected.size(), schedule, skipped, filledDays);
        String coverageMessage = buildCoverageMessage(selected.size(), schedule.size(), filledDays, draft.getDayCount());

        TripResponse trip = tripService.createTrip(CreateTripRequest.builder()
                .name(draft.getTripName() != null && !draft.getTripName().isBlank() ? draft.getTripName() : draft.getCityName())
                .destination(draft.getCityName())
                .destinationPlaceId(draft.getCityId())
                .destinationLat(draft.getCityLat())
                .destinationLng(draft.getCityLng())
                .startDate(draft.getStartDate())
                .endDate(draft.getEndDate())
                .currency("VND")
                .build(), userId);

        if (generationSummary != null && !generationSummary.isBlank()) {
            trip = tripService.updateTrip(trip.getId(),
                    UpdateTripRequest.builder().description(generationSummary).build(),
                    userId);
        }

        for (ScheduledCandidate item : schedule) {
            activityRepository.insert(toActivity(trip.getId(), item, userId, visitTips));
        }

        aiTripRepository.completeDraft(draftId, userId, request.getIdempotencyKey(), trip.getId());

        List<String> skippedNames = skipped.stream()
                .map(AiTripCandidateResponse::getName)
                .filter(name -> name != null && !name.isBlank())
                .toList();

        return AiTripConfirmResponse.builder()
                .trip(trip)
                .selectedCount(selected.size())
                .scheduledCount(schedule.size())
                .filledDays(filledDays)
                .totalDays(draft.getDayCount())
                .coverageMessage(coverageMessage)
                .generationSummary(generationSummary)
                .skippedPlaceNames(skippedNames)
                .build();
    }

    private void validateGenerateRequest(AiTripGenerateRequest request) {
        if (request.getCityLat() == null || request.getCityLng() == null) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "City latitude and longitude are required");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Start date must be before end date");
        }
        int dayCount = resolveDayCount(request);
        if (dayCount <= 0 || dayCount > 30) {
            throw new BusinessException(ErrorConstant.INVALID_PARAMETERS, "Day count must be between 1 and 30");
        }
    }

    private int resolveDayCount(AiTripGenerateRequest request) {
        if (request.getDayCount() != null && request.getDayCount() > 0) {
            return request.getDayCount();
        }
        return (int) (request.getEndDate().toEpochDay() - request.getStartDate().toEpochDay()) + 1;
    }

    private String defaultTripName(AiTripGenerateRequest request) {
        return request.getTripName() != null && !request.getTripName().isBlank()
                ? request.getTripName().trim()
                : request.getCityName();
    }

    private List<PlaceGroup> normalizeGroups(List<PlaceGroup> groups) {
        EnumSet<PlaceGroup> normalized = EnumSet.noneOf(PlaceGroup.class);
        if (groups == null || groups.isEmpty()) {
            normalized.addAll(Arrays.stream(PlaceGroup.values())
                    .filter(group -> group != PlaceGroup.OTHER)
                    .toList());
        } else {
            groups.stream()
                    .filter(group -> group != null && group != PlaceGroup.OTHER)
                    .forEach(normalized::add);
        }
        normalized.add(PlaceGroup.ACCOMMODATION);
        return normalized.stream().toList();
    }

    private String normalizePace(String pace) {
        if (pace == null || pace.isBlank()) {
            return "BALANCED";
        }
        String value = pace.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "RELAXED", "EAGER" -> value;
            default -> "BALANCED";
        };
    }

    private int limitForTier(String tier) {
        return "PRO".equalsIgnoreCase(tier) ? PRO_LIMIT : FREE_LIMIT;
    }

    private List<AiTripCandidateResponse> collectCandidates(AiTripGenerateRequest request, List<PlaceGroup> groups) {
        int dayCount = resolveDayCount(request);
        List<AiTripCandidateResponse> candidates = new ArrayList<>();

        for (PlaceGroup group : groups) {
            int groupLimit = calculateGroupLimit(group, dayCount);
            List<Place> places = findPlaces(request, group, groupLimit);
            for (Place place : places) {
                candidates.add(fromPlace(place));
            }
        }

        int bookingLimit = dayCount;
        candidates.addAll(findBookings(request, bookingLimit).stream()
                .map(this::fromBooking)
                .toList());

        Map<String, AiTripCandidateResponse> deduped = new LinkedHashMap<>();
        for (AiTripCandidateResponse candidate : candidates) {
            String key = normalizeKey(candidate.getName() + "|" + candidate.getAddress());
            deduped.putIfAbsent(key, candidate);
        }

        return deduped.values().stream()
                .sorted(Comparator.comparing(this::candidateScore).reversed())
                .toList();
    }

    private int calculateGroupLimit(PlaceGroup group, int dayCount) {
        return switch (group) {
            case ACCOMMODATION -> Math.max(2, dayCount / 3);
            case FOOD_AND_DRINK -> dayCount * 4;
            default -> dayCount * 5;
        };
    }

    private List<Place> findPlaces(AiTripGenerateRequest request, PlaceGroup group, int limit) {
        return placeRepository.findNearby(null, request.getCityLat(), request.getCityLng(),
                BigDecimal.valueOf(SEARCH_RADIUS_KM), null, List.of(group.name()), BigDecimal.valueOf(3.5), false, limit, 0);
    }

    private List<ActivityBooking> findBookings(AiTripGenerateRequest request, int limit) {
        double lat = request.getCityLat().doubleValue();
        double lng = request.getCityLng().doubleValue();
        double latDelta = SEARCH_RADIUS_KM / 111.0;
        double lngDelta = SEARCH_RADIUS_KM / (111.0 * Math.max(0.2, Math.cos(Math.toRadians(lat))));
        return activityBookingRepository.findWithinRadius(ActivityBookingGeoSearchParams.builder()
                .latitude(request.getCityLat())
                .longitude(request.getCityLng())
                .radiusKm(SEARCH_RADIUS_KM)
                .minLat(lat - latDelta)
                .maxLat(lat + latDelta)
                .minLng(lng - lngDelta)
                .maxLng(lng + lngDelta)
                .minRating(BigDecimal.valueOf(3.5))
                .limit(limit)
                .offset(0)
                .build());
    }

    private String formatCandidatesForAI(List<AiTripCandidateResponse> candidates) {
        List<Map<String, Object>> formatted = new ArrayList<>();
        for (AiTripCandidateResponse c : candidates) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", c.getId());
            item.put("name", c.getName());
            item.put("placeGroup", c.getPlaceGroup());
            item.put("category", c.getCategory());
            
            // Use full description for AI context (not trimmed)
            if (c.getDescription() != null && !c.getDescription().isBlank()) {
                item.put("description", c.getDescription());
            }
            
            item.put("address", c.getAddress());
            item.put("lat", c.getLat());
            item.put("lng", c.getLng());
            item.put("rating", c.getRating());
            item.put("reviewCount", c.getReviewCount());
            item.put("visitDurationMinutes", c.getVisitDurationMinutes());
            
            if (c.getPriceAmount() != null) {
                item.put("price", c.getPriceAmount() + " " + c.getPriceCurrency());
            }
            
            formatted.add(item);
        }
        return JsonUtils.toJson(formatted);
    }

    private AiTripCandidateResponse fromPlace(Place place) {
        String aiDesc = place.getAiDescription() != null && !place.getAiDescription().isBlank()
                ? place.getAiDescription()
                : place.getDescriptions();
        Integer duration = place.getVisitDurationMinutes() != null
                ? place.getVisitDurationMinutes()
                : defaultDurationForGroup(place.getPlaceGroup());
        return AiTripCandidateResponse.builder()
                .id("PLACE:" + place.getId())
                .sourceType("PLACE")
                .sourceId(place.getId().toString())
                .name(place.getTitle())
                .description(trimText(aiDesc, 320))
                .address(place.getAddress())
                .lat(place.getLatitude())
                .lng(place.getLongitude())
                .rating(place.getReviewRating())
                .reviewCount(place.getReviewCount())
                .photoUrl(place.getThumbnail())
                .placeGroup(place.getPlaceGroup() != null ? place.getPlaceGroup().name() : PlaceGroup.OTHER.name())
                .category(place.getCategory())
                .visitDurationMinutes(duration)
                .durationText(formatDuration(duration))
                .build();
    }

    private AiTripCandidateResponse fromBooking(ActivityBooking booking) {
        Integer duration = booking.getVisitDurationMinutes();
        if (duration == null && booking.getDurationHours() != null) {
            duration = booking.getDurationHours().multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValue();
        }
        if (duration == null || duration <= 0) {
            duration = 180;
        }
        return AiTripCandidateResponse.builder()
                .id("BOOKING:" + booking.getId())
                .sourceType("BOOKING")
                .sourceId(booking.getId().toString())
                .name(booking.getTitle())
                .description(trimText(booking.getDescription(), 320))
                .address(firstNonBlank(booking.getActivityAddress(), booking.getDepartingFrom()))
                .lat(booking.getSearchLat() != null ? BigDecimal.valueOf(booking.getSearchLat()) : null)
                .lng(booking.getSearchLng() != null ? BigDecimal.valueOf(booking.getSearchLng()) : null)
                .rating(booking.getRating())
                .reviewCount(booking.getReviewCount())
                .photoUrl(booking.getThumbnail())
                .placeGroup(PlaceGroup.ATTRACTIONS.name())
                .category("activity")
                .visitDurationMinutes(duration)
                .durationText(firstNonBlank(booking.getDurationRaw(), formatDuration(duration)))
                .bookingId(booking.getId())
                .bookingSource(booking.getSource())
                .priceAmount(booking.getPriceAmount())
                .priceCurrency(booking.getPriceCurrency())
                .build();
    }

    private Integer defaultDurationForGroup(PlaceGroup group) {
        if (group == null) return 120;
        return switch (group) {
            case FOOD_AND_DRINK -> 90;
            case SHOPPING_AND_MARKET -> 120;
            case ACCOMMODATION -> 60;
            case CULTURE_AND_HERITAGE, NATURE_AND_OUTDOORS, ATTRACTIONS -> 150;
            case OTHER -> 120;
        };
    }

    private double candidateScore(AiTripCandidateResponse candidate) {
        double rating = candidate.getRating() != null ? candidate.getRating().doubleValue() : 0;
        double reviews = candidate.getReviewCount() != null ? Math.log10(candidate.getReviewCount() + 1) : 0;
        double sourceBoost = "BOOKING".equals(candidate.getSourceType()) ? 0.15 : 0;
        return rating + reviews * 0.35 + sourceBoost;
    }

    private List<AiTripCandidateResponse> rankCandidatesWithAi(AiTripGenerateRequest request,
                                                                   List<AiTripCandidateResponse> candidates) {
        if (candidates.isEmpty()) {
            return candidates;
        }
        String system = AI_TRIP_SYSTEM_CONTEXT + """
                You are an expert travel curator specializing in Vietnam destinations.
                Your role: rank real places for authentic, memorable experiences.
                
                Core principles:
                - Use ONLY candidate IDs provided - never invent places
                - Prioritize local authenticity over generic tourist traps
                - Consider real-world logistics: weather, timing, crowds, accessibility
                - Balance iconic must-sees with hidden gems
                - Respect cultural sensitivities and local customs
                - Think like a local guide, not a tour operator
                
                Return strict JSON only.
                """ + "\n" + AiTripLanguageSupport.aiLanguageRule();
        
        String budgetContext = buildBudgetContext(request);
        String groupContext = buildGroupContext(request);
        String dietaryContext = buildDietaryContext(request);
        String activityContext = buildActivityTypeContext(request);
        
        String prompt = """
                ## TRIP CONTEXT
                Destination: %s (lat: %s, lng: %s)
                Dates: %s to %s (%d days, %s season)
                Pace: %s | Style: %s
                %s
                %s
                %s
                %s
                User notes: %s
                
                ## CLIMATE INTELLIGENCE
                Vietnam regional weather patterns:
                • North (lat>20): Nov-Mar cool/dry 15-25°C, Apr-Oct hot/rainy 25-35°C
                • Central (lat 12-20): Sep-Dec rainy/flood risk, Jan-Aug dry/hot 25-38°C
                • South (lat<12): Nov-Apr dry 25-32°C, May-Oct rainy season
                • Highlands: Always 5-10°C cooler, mist common morning/evening
                
                Current trip timing: Infer likely weather and adjust recommendations.
                
                ## RANKING RULES (Priority Order)
                
                ### 1. ACCOMMODATION (Rank Low - End of List)
                - Place near bottom unless luxury villa with unique experience value
                - Max 1 per 3-4 days for multi-city trips
                
                ### 2. FOOD & DINING (Strategic Meal Windows)
                - Match to natural meal times:
                  * Breakfast spots: 07:00-09:30
                  * Lunch venues: 11:30-13:30  
                  * Dinner restaurants: 18:00-20:30
                - Prioritize: High local ratings > tourist reviews
                - Avoid: Restaurants directly adjacent to major tourist sites
                - Consider: Dietary restrictions = %s
                
                ### 3. WEATHER-ADAPTIVE RANKING
                - Hot season: Rank indoor/shaded activities higher for midday
                - Rainy season: Prioritize covered markets, museums, cafes as buffers
                - Cool season: Rank outdoor nature activities higher
                - If includeBackupActivities=true: Note indoor alternatives for outdoor spots
                
                ### 4. GROUP SUITABILITY
                Group composition: %s
                - If children: Avoid overly strenuous activities, rank interactive experiences high
                - If elderly: Prioritize wheelchair-accessible, low-physical-demand sites
                - If family: Rank educational + fun experiences high
                
                ### 5. ACTIVITY TYPE PREFERENCES
                Requested types: %s
                - If Photography: Rank scenic viewpoints, golden hour spots (sunrise/sunset) high
                - If Food: Emphasize street food, cooking classes, local markets
                - If Culture: Museums, temples, traditional craft workshops
                - If Adventure: Trekking, water sports, cycling routes
                - If Nature: National parks, waterfalls, scenic landscapes
                
                ### 6. AUTHENTICITY FILTER
                - Rank higher: Places frequented by locals, family-run establishments
                - Rank lower: Obvious tourist traps, overly commercialized "cultural shows"
                - Verify: Recent reviews mention "authentic", "local", "hidden gem"
                
                ### 7. QUALITY SIGNALS
                - Rating ≥4.5 + reviews >500: Consistently excellent
                - Rating 4.2-4.5 + reviews >100: Good with character
                - New places (<50 reviews) but 5.0: Worth considering if locally endorsed
                
                ### 8. CULTURAL & SAFETY CONSIDERATIONS
                - Religious sites: Note dress code (cover shoulders/knees), respectful behavior
                - Sacred sites: Flag photography restrictions
                - Remote areas: Note safety precautions, best travel times
                - Local customs: Highlight any etiquette requirements
                
                ### 9. BOOKING LOGISTICS
                - High-demand venues: Flag "book 1-2 weeks advance"
                - Sunset spots: "Arrive 30min early for best views"
                - Popular attractions: "Visit early morning or late afternoon to avoid crowds"
                
                ### 10. VARIETY & PACING
                - Avoid 4+ consecutive stops of same placeGroup
                - Alternate intensity: Museum → café → active → relaxed
                - Balance: 60%% iconic sights, 40%% local discoveries
                
                ## OUTPUT FORMAT
                Return JSON only:
                {
                  "candidateIds": ["id1", "id2", ...],
                  "reasons": {
                    "id1": "Concrete reason: weather fit + group suitability + authenticity + timing window"
                  }
                }
                
                ## CANDIDATES
                %s
                """.formatted(
                request.getCityName(),
                request.getCityLat(), request.getCityLng(),
                request.getStartDate(), request.getEndDate(),
                resolveDayCount(request),
                inferSeason(request.getStartDate()),
                normalizePace(request.getPace()),
                request.getTravelStyle() != null ? request.getTravelStyle() : "Balanced exploration",
                groupContext.isEmpty() ? "" : "Group: " + groupContext,
                budgetContext.isEmpty() ? "" : "Budget: " + budgetContext,
                dietaryContext.isEmpty() ? "" : "Dietary: " + dietaryContext,
                activityContext.isEmpty() ? "" : "Activities: " + activityContext,
                Optional.ofNullable(request.getPreferenceText()).orElse("none"),
                dietaryContext.isEmpty() ? "None" : dietaryContext,
                groupContext.isEmpty() ? "General travelers" : groupContext,
                activityContext.isEmpty() ? "Balanced variety" : activityContext,
                formatCandidatesForAI(candidates));

        return aiClient.completeJson(system, prompt)
                .map(json -> applyRankResult(json, candidates))
                .orElse(candidates);
    }
    
    private String inferSeason(LocalDate startDate) {
        int month = startDate.getMonthValue();
        if (month >= 11 || month <= 3) return "cool/dry";
        if (month >= 4 && month <= 6) return "hot";
        return "rainy";
    }

    private List<AiTripCandidateResponse> applyRankResult(String rawJson, List<AiTripCandidateResponse> candidates) {
        try {
            JsonNode root = objectMapper.readTree(extractJson(rawJson));
            JsonNode idsNode = root.get("candidateIds");
            JsonNode reasonsNode = root.get("reasons");
            if (idsNode == null || !idsNode.isArray()) {
                return candidates;
            }
            Map<String, AiTripCandidateResponse> byId = candidates.stream()
                    .collect(Collectors.toMap(AiTripCandidateResponse::getId, Function.identity(), (a, b) -> a));
            List<AiTripCandidateResponse> ordered = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                AiTripCandidateResponse candidate = byId.remove(idNode.asText());
                if (candidate != null) {
                    if (reasonsNode != null && reasonsNode.has(candidate.getId())) {
                        candidate.setAiReason(reasonsNode.get(candidate.getId()).asText());
                    }
                    ordered.add(candidate);
                }
            }
            ordered.addAll(byId.values());
            return ordered;
        } catch (Exception e) {
            log.warn("Failed to parse AI candidate ranking: {}", e.getMessage());
            return candidates;
        }
    }

    private List<ScheduledCandidate> scheduleCandidatesWithAi(AiTripDraft draft,
                                                                  List<AiTripCandidateResponse> selected) {
        if (selected.isEmpty()) {
            return List.of();
        }
        List<AiTripCandidateResponse> ordered = orderByAiOrDistance(draft, selected);
        return assignTimes(draft, ordered);
    }

    private List<AiTripCandidateResponse> orderByAiOrDistance(AiTripDraft draft,
                                                                  List<AiTripCandidateResponse> selected) {
        String system = AI_TRIP_SYSTEM_CONTEXT + """
                You are an expert itinerary builder specializing in Vietnam travel logistics.
                Your role: sequence real places into practical daily schedules.
                
                Core principles:
                - Use ONLY candidate IDs provided by the server
                - Minimize backtracking - cluster geographically
                - Respect local rhythms: meal times, siesta hours, prayer times
                - Consider weather patterns for outdoor timing
                - Balance energy levels throughout each day
                - Think like a local guide optimizing for experience + logistics
                
                Return strict JSON only.
                """ + "\n" + AiTripLanguageSupport.aiLanguageRule();
        
        String budgetContext = buildBudgetContextFromDraft(draft);
        String groupContext = buildGroupContextFromDraft(draft);
        String dietaryContext = buildDietaryContextFromDraft(draft);
        String activityContext = buildActivityTypeContextFromDraft(draft);
        
        String prompt = """
                ## TRIP CONTEXT
                City: %s (lat: %s, lng: %s)
                Dates: %s to %s (%d days, %s season)
                Pace: %s | Style: %s
                %s
                %s
                %s
                %s
                User notes: %s
                
                ## CLIMATE & TIMING INTELLIGENCE
                Vietnam weather by latitude + month:
                • North (lat>20): Nov-Mar cool/dry, Apr-Oct hot/rainy
                • Central (lat 12-20): Sep-Dec rainy/floods, Jan-Aug dry/hot
                • South (lat<12): Nov-Apr dry, May-Oct rainy
                • Highlands: 5-10°C cooler, morning mist common
                
                Optimal activity timing by weather:
                • Hot/sunny: Outdoor activities before 10:00 or after 16:00
                • Rainy season: Indoor buffers between outdoor stops
                • Cool season: Flexibility for all-day outdoor activities
                
                ## DAILY SCHEDULING RULES (STRICT)
                
                ### Core Rhythm - FULL DAY FLEXIBILITY
                Activities can span entire day (05:00-22:00) based on:
                - **Place-specific optimal times** (not rigid 9-to-5)
                - **Weather conditions** (avoid midday heat/rain)
                - **Seasonal patterns** (sunrise/sunset timing)
                - **Crowd avoidance** (early morning vs peak hours)
                - **Cultural context** (market hours, prayer times, siesta)
                
                Daily stop limits: RELAXED=5 stops, BALANCED=7 stops, EAGER=9 stops
                
                ### PLACE-SPECIFIC TIMING INTELLIGENCE (Vietnam Context)
                
                **Historical/Monument Sites:**
                - Ho Chi Minh Mausoleum: Must arrive 07:30-08:00 (opens 08:00, long queues, closes 11:00)
                - Imperial City Hue: Early morning 07:00-09:00 OR late afternoon 15:00-17:00 (avoid midday heat)
                - Ancient Town Hoi An: Late afternoon 16:00-20:00 (cooler, lantern lighting at dusk)
                - Cu Chi Tunnels: Early morning 07:00-09:00 (cooler, fewer crowds)
                
                **Natural Attractions:**
                - Ha Long Bay: Full day 08:00-17:00 (cruise timing)
                - Sapa Rice Terraces: Sunrise 05:30-07:00 OR golden hour 16:00-18:00 (photography)
                - Phong Nha Caves: Morning 08:00-12:00 (cooler inside, avoid afternoon rain)
                - Mekong Delta: Early morning 06:00-10:00 (floating markets active)
                
                **Lakes & Waterfront:**
                - Hoan Kiem Lake (Hanoi): Early morning 05:00-07:00 (locals exercising) OR evening 17:00-20:00 (cooler, lit up)
                - West Lake (Ho Tay): Late afternoon 16:00-19:00 (sunset, avoid midday heat)
                - Hoan Kiem Walking Street: Friday-Sunday evenings only 19:00-23:00
                
                **Markets:**
                - Ben Thanh Market: Early 06:00-08:00 (fresh produce, authentic) OR evening 18:00-21:00 (night market)
                - Dong Xuan Market: Morning 06:00-10:00 (locals shopping, avoid midday heat)
                - Weekend Night Markets: 18:00-23:00 only
                
                **Museums & Indoor:**
                - Best during hot hours: 11:00-15:00 (air conditioned escape)
                - Check Monday closures (many museums closed)
                - War museums: Morning when energy is fresh for heavy content
                
                **Temples & Pagodas:**
                - Early morning 06:00-08:00 (peaceful, monks chanting, incense offering)
                - Late afternoon 16:00-18:00 (golden light, locals praying after work)
                - Avoid: Midday 11:00-14:00 (hot, empty, harsh light)
                
                **Food Experiences:**
                - Street food breakfast: 06:00-08:00 (pho, banh mi freshly made)
                - Lunch spots: 11:30-13:00 (authentic local timing)
                - Coffee culture: 14:00-16:00 (Vietnamese afternoon ritual)
                - Dinner: 18:00-20:00 (family dining time)
                - Late night street food: 21:00-23:00 (bun cha, bun rieu)
                
                **Rooftop Bars & Viewpoints:**
                - Sunset timing: Arrive 30min before sunset (varies by season)
                - Night skyline: 19:00-22:00
                
                **Traffic & Rush Hour Avoidance:**
                - Hanoi/HCMC rush hours: 07:00-09:00 and 17:00-19:00
                - Schedule indoor activities OR walking-distance transitions during rush hour
                - Motorbike tours: Mid-morning 09:00-11:00 OR late afternoon 15:00-17:00
                
                ### 1. MEAL WINDOW PLACEMENT (MANDATORY)
                FOOD_AND_DRINK stops MUST land in meal windows:
                - **Breakfast slot**: First FOOD stop → before 09:30
                - **Lunch slot**: Second FOOD stop → 11:30-13:30
                - **Dinner slot**: Third FOOD stop → 18:00-20:30
                
                Vietnam meal culture:
                • Breakfast: 06:30-08:30 (pho, banh mi from street stalls)
                • Lunch: 11:30-13:00 (main meal, family-run restaurants)
                • Dinner: 18:00-19:30 (lighter than lunch, social)
                
                Dietary restrictions: %s
                If restrictions present, prioritize verified restaurants over street food.
                
                If more FOOD stops than meal windows: Push extras to next day.
                
                ### 2. ACCOMMODATION PLACEMENT
                - MUST be last stop of each day (check-in logic)
                - If multiple ACCOMMODATION: Keep only most relevant per day
                - Evening arrival time: 17:00-18:00 latest
                
                ### 3. OUTDOOR ACTIVITY TIMING (WEATHER-ADAPTIVE)
                NATURE_AND_OUTDOORS placement:
                - **Hot season (Apr-Oct)**: 
                  * Morning: 05:30-10:00 (sunrise, cool)
                  * Evening: 16:00-19:00 (sunset, cooler)
                  * NEVER: 11:00-15:00 (peak heat, harsh sun)
                - **Cool season (Nov-Mar)**: 
                  * Flexible: 07:00-18:00 (comfortable all day)
                  * Golden hours: 06:00-08:00 sunrise, 16:30-18:00 sunset
                - **Rainy season**: 
                  * Morning preferred: 07:00-11:00 (rain usually afternoon)
                  * Indoor backup: Have alternative ready
                
                Photography spots (if activity type = Photography):
                - **Sunrise**: 05:30-07:00 (highlands, rice terraces, Ha Long Bay)
                - **Blue hour**: 06:00-07:00 morning, 18:00-19:00 evening
                - **Golden hour**: 16:30-18:00 (ancient towns, pagodas, lakes)
                - **Night photography**: 19:00-22:00 (city lights, lanterns, street scenes)
                - **Avoid**: Midday 11:00-15:00 (harsh shadows, washed out colors)
                
                ### 4. GEOGRAPHIC CLUSTERING (CRITICAL)
                - Same district/neighborhood stops: Consecutive sequence
                - Walking distance (<1km): Back-to-back placement
                - Minimize zig-zag: Use lat/lng to avoid backtracking
                - Consider: Traffic patterns, one-way streets, hill slopes
                
                ### 5. GROUP-SPECIFIC PACING
                %s
                - **Children present**: Max 4-5 hours active time, include play breaks
                - **Elderly members**: Avoid back-to-back walking, café breaks between sites
                - **General travelers**: Standard pacing per pace setting
                
                ### 6. CULTURAL & RELIGIOUS TIMING (PLACE-SPECIFIC)
                - **Ho Chi Minh Mausoleum**: 08:00-11:00 ONLY (closed afternoon), arrive 07:30 to queue
                - **Temples/Pagodas**: 
                  * Best: 06:00-08:00 (morning prayers, incense, peaceful)
                  * Good: 16:00-18:00 (evening prayers, golden light)
                  * Avoid: 11:00-14:00 (empty, hot, no atmosphere)
                - **Markets**: 
                  * Authentic: 06:00-09:00 (locals shopping, fresh produce)
                  * Tourist-friendly: 09:00-17:00 (shops fully open)
                  * Night markets: 18:00-23:00 (specific locations only)
                - **Museums**: 
                  * Strategic: 11:00-15:00 during hot hours (air-conditioned)
                  * Check: Many close Mondays
                - **Walking Streets**: 
                  * Hoan Kiem: Friday-Sunday 19:00-23:00 ONLY
                  * Nguyen Hue: Daily evenings 18:00-22:00
                - **Coffee Culture**: 14:00-16:00 (Vietnamese afternoon ritual)
                - **Rush Hour**: 07:00-09:00 and 17:00-19:00 (avoid transport during these)
                
                ### 7. ENERGY FLOW BALANCE
                Alternate intensity within each day:
                - High energy (hiking, cycling) → Low energy (café, scenic viewing)
                - Active morning → Relaxed lunch → Moderate afternoon
                - Never: 3+ consecutive high-energy activities
                
                ### 8. PLACE GROUP VARIETY
                - Avoid 3+ consecutive same placeGroup
                - Example good flow: Temple → Market → Café → Museum → Restaurant
                - Example bad flow: Museum → Museum → Museum → Gallery
                
                ### 9. ADVANCE BOOKING CONSIDERATIONS
                For popular/sunset/special experience places:
                - Schedule with 30min buffer before official time
                - Note in reasons: "Arrive early" or "Book 1 week advance"
                
                ### 10. BACKUP PLANNING
                If includeBackupActivities=true:
                - For each outdoor stop, note nearest indoor alternative
                - Museums, covered markets, shopping malls within 15min
                
                ## OUTPUT FORMAT
                Return JSON only (same candidates, reordered):
                {
                  "candidateIds": ["id1", "id2", "id3", ...]
                }
                
                Order reflects: Day 1 stops, then Day 2, then Day 3, etc.
                Geographic clustering + meal windows + energy balance = optimal sequence.
                
                ## SELECTED CANDIDATES
                %s
                """.formatted(
                draft.getCityName(),
                draft.getCityLat(), draft.getCityLng(),
                draft.getStartDate(), draft.getEndDate(),
                draft.getDayCount(),
                inferSeasonFromDraft(draft),
                draft.getPace(),
                draft.getTravelStyle() != null ? draft.getTravelStyle() : "Balanced",
                groupContext.isEmpty() ? "" : "Group: " + groupContext,
                budgetContext.isEmpty() ? "" : "Budget: " + budgetContext,
                dietaryContext.isEmpty() ? "" : "Dietary: " + dietaryContext,
                activityContext.isEmpty() ? "" : "Activities: " + activityContext,
                Optional.ofNullable(draft.getPreferenceText()).orElse("none"),
                dietaryContext.isEmpty() ? "None" : dietaryContext,
                groupContext.isEmpty() ? "General travelers" : groupContext,
                formatCandidatesForAI(selected));
        return aiClient.completeJson(system, prompt)
                .map(json -> parseCandidateOrder(json, selected))
                .orElseGet(() -> nearestNeighborOrder(draft, selected));
    }
    
    private String inferSeasonFromDraft(AiTripDraft draft) {
        int month = draft.getStartDate().getMonthValue();
        if (month >= 11 || month <= 3) return "cool/dry";
        if (month >= 4 && month <= 6) return "hot";
        return "rainy";
    }

    private List<AiTripCandidateResponse> parseCandidateOrder(String rawJson, List<AiTripCandidateResponse> selected) {
        try {
            JsonNode idsNode = objectMapper.readTree(extractJson(rawJson)).get("candidateIds");
            if (idsNode == null || !idsNode.isArray()) {
                return nearestNeighborOrder(null, selected);
            }
            Map<String, AiTripCandidateResponse> byId = selected.stream()
                    .collect(Collectors.toMap(AiTripCandidateResponse::getId, Function.identity(), (a, b) -> a));
            List<AiTripCandidateResponse> ordered = new ArrayList<>();
            for (JsonNode idNode : idsNode) {
                AiTripCandidateResponse candidate = byId.remove(idNode.asText());
                if (candidate != null) {
                    ordered.add(candidate);
                }
            }
            ordered.addAll(byId.values());
            return ordered;
        } catch (Exception e) {
            return nearestNeighborOrder(null, selected);
        }
    }

    private List<AiTripCandidateResponse> nearestNeighborOrder(AiTripDraft draft, List<AiTripCandidateResponse> input) {
        List<AiTripCandidateResponse> remaining = new ArrayList<>(input);
        List<AiTripCandidateResponse> ordered = new ArrayList<>();
        BigDecimal currentLat = draft != null ? draft.getCityLat() : null;
        BigDecimal currentLng = draft != null ? draft.getCityLng() : null;

        while (!remaining.isEmpty()) {
            final BigDecimal lat = currentLat;
            final BigDecimal lng = currentLng;
            AiTripCandidateResponse next = remaining.stream()
                    .min(Comparator.comparingDouble(candidate -> distanceKm(lat, lng, candidate.getLat(), candidate.getLng())))
                    .orElse(remaining.get(0));
            ordered.add(next);
            remaining.remove(next);
            currentLat = next.getLat();
            currentLng = next.getLng();
        }
        return ordered;
    }

    private List<ScheduledCandidate> assignTimes(AiTripDraft draft, List<AiTripCandidateResponse> ordered) {
        int capMinutes = switch (draft.getPace()) {
            case "RELAXED" -> 9 * 60;   // 9 hours of activities (more breaks)
            case "EAGER" -> 13 * 60;    // 13 hours (early start, late end)
            default -> 11 * 60;         // 11 hours balanced
        };
        
        int day = 1;
        int used = 0;
        LocalTime cursor = LocalTime.of(7, 0); // Start at 7am for flexibility
        List<ScheduledCandidate> schedule = new ArrayList<>();
        AiTripCandidateResponse previous = null;

        for (AiTripCandidateResponse candidate : ordered) {
            if (day > draft.getDayCount()) {
                break;
            }
            
            // Determine optimal start time based on place type
            LocalTime optimalStart = determineOptimalStartTime(candidate, cursor, day == 1 && schedule.isEmpty());
            if (optimalStart != null && optimalStart.isAfter(cursor)) {
                cursor = optimalStart;
            }
            
            int duration = clamp(candidate.getVisitDurationMinutes() != null ? candidate.getVisitDurationMinutes() : 120, 45, 480);
            int travelGap = previous == null ? 0 : travelGapMinutes(previous, candidate);
            int mealGap = needsMealBreak(cursor.plusMinutes(travelGap), duration) ? 60 : 0;
            int block = duration + travelGap + mealGap;
            
            if (used > 0 && used + block > capMinutes) {
                day++;
                used = 0;
                cursor = LocalTime.of(7, 0);
                previous = null;
                travelGap = 0;
                
                // Re-check optimal time for first activity of new day
                optimalStart = determineOptimalStartTime(candidate, cursor, true);
                if (optimalStart != null) {
                    cursor = optimalStart;
                }
                
                mealGap = needsMealBreak(cursor, duration) ? 60 : 0;
                block = duration + mealGap;
            }
            if (day > draft.getDayCount()) {
                break;
            }
            if (used > 0) {
                cursor = cursor.plusMinutes(travelGap);
            }
            if (mealGap > 0) {
                cursor = cursor.plusMinutes(mealGap);
            }
            LocalTime start = cursor;
            LocalTime end = start.plusMinutes(duration);
            schedule.add(new ScheduledCandidate(candidate, day, start, end, recommendTransport(previous, candidate)));
            used += block;
            cursor = end;
            previous = candidate;
        }
        return schedule;
    }
    
    private LocalTime determineOptimalStartTime(AiTripCandidateResponse candidate, LocalTime currentTime, boolean isFirstOfDay) {
        String group = candidate.getPlaceGroup();
        String name = candidate.getName() != null ? candidate.getName().toLowerCase() : "";
        
        // Ho Chi Minh Mausoleum - must arrive early to queue
        if (name.contains("mausoleum") || name.contains("lăng bác") || name.contains("lang bac")) {
            return isFirstOfDay ? LocalTime.of(7, 30) : null; // 7:30am to queue for 8am opening
        }
        
        // Markets - early morning for authentic experience
        if (group != null && group.equals("SHOPPING_AND_MARKET") && isFirstOfDay) {
            return LocalTime.of(6, 30); // 6:30am for local market vibe
        }
        
        // Sunrise viewpoints
        if (name.contains("viewpoint") || name.contains("sunrise") || name.contains("terrace")) {
            return isFirstOfDay ? LocalTime.of(5, 30) : null;
        }
        
        // Temples/Pagodas - early morning prayers
        if (group != null && group.equals("CULTURE_AND_HERITAGE") && 
            (name.contains("temple") || name.contains("pagoda") || name.contains("chùa") || name.contains("đền"))) {
            if (isFirstOfDay && currentTime.isBefore(LocalTime.of(8, 0))) {
                return LocalTime.of(6, 30); // Early for peaceful atmosphere
            }
        }
        
        // Walking streets - evening only
        if (name.contains("walking street") || name.contains("phố đi bộ")) {
            return LocalTime.of(19, 0); // 7pm when pedestrian zone opens
        }
        
        // Rooftop bars - sunset timing
        if (name.contains("rooftop") || name.contains("skyline")) {
            return LocalTime.of(17, 0); // Arrive before sunset
        }
        
        // Default: no specific timing requirement
        return null;
    }

    private int travelGapMinutes(AiTripCandidateResponse from, AiTripCandidateResponse to) {
        if (from.getLat() == null || from.getLng() == null || to.getLat() == null || to.getLng() == null) {
            return 30;
        }
        double km = haversineKm(
                from.getLat().doubleValue(),
                from.getLng().doubleValue(),
                to.getLat().doubleValue(),
                to.getLng().doubleValue()
        );
        if (km < 1.0) return 15;
        if (km < 3.0) return 25;
        if (km < 8.0) return 40;
        if (km < 20.0) return 60;
        return 90;
    }

    private boolean needsMealBreak(LocalTime start, int durationMinutes) {
        LocalTime end = start.plusMinutes(durationMinutes);
        return start.isBefore(LocalTime.of(13, 0)) && end.isAfter(LocalTime.of(12, 0));
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double radiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return radiusKm * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Activity toActivity(UUID tripId, ScheduledCandidate item, UUID userId, Map<String, String> visitTips) {
        AiTripCandidateResponse candidate = item.candidate();
        String visitTip = visitTips.getOrDefault(candidate.getId(), fallbackVisitTip(candidate, item));
        return Activity.builder()
                .id(UUID.randomUUID())
                .tripId(tripId)
                .dayNumber(item.dayNumber())
                .placeId("PLACE".equals(candidate.getSourceType()) ? candidate.getSourceId() : null)
                .name(candidate.getName())
                .address(candidate.getAddress())
                .lat(candidate.getLat())
                .lng(candidate.getLng())
                .startTime(item.startTime())
                .endTime(item.endTime())
                .category(activityCategoryFor(candidate))
                .transportMode(item.transportMode())
                .rating(candidate.getRating())
                .photoUrl(candidate.getPhotoUrl())
                .description(trimText(visitTip, 280))
                .status(ActivityStatus.CONFIRMED)
                .addedBy(userId)
                .isAccommodation(PlaceGroup.ACCOMMODATION.name().equals(candidate.getPlaceGroup()))
                .isStartingPoint(false)
                .bookingId(candidate.getBookingId())
                .bookingSource(candidate.getBookingSource())
                .build();
    }

    private String activityCategoryFor(AiTripCandidateResponse candidate) {
        String category = normalizeKey(candidate.getCategory());
        String name = normalizeKey(candidate.getName());
        String text = category + " " + name + " " + normalizeKey(candidate.getDescription());
        if (containsAny(text, "restaurant", "food", "cafe", "coffee", "bar", "pub", "bakery", "drink", "quan", "nha hang")) {
            return "restaurant";
        }
        if (containsAny(text, "hotel", "hostel", "resort", "homestay", "accommodation")) {
            return "hotel";
        }
        if (containsAny(text, "beach", "island", "bay", "coast", "bien", "dao")) {
            return "beach";
        }
        if (containsAny(text, "temple", "pagoda", "church", "cathedral", "shrine", "chua", "den", "nha tho")) {
            return "spiritual";
        }
        if (containsAny(text, "park", "garden", "mountain", "lake", "waterfall", "nature", "trail", "forest", "vuon", "ho", "thac")) {
            return "nature";
        }
        if (containsAny(text, "market", "mall", "shopping", "shop", "cho")) {
            return "shopping";
        }
        if (containsAny(text, "museum", "gallery", "heritage", "historic", "palace", "citadel", "monument", "bao tang")) {
            return "museum";
        }
        if (containsAny(text, "show", "theater", "cinema", "night", "club", "entertainment", "performance")) {
            return "entertainment";
        }
        if (candidate.getPlaceGroup() == null) {
            return "attraction";
        }
        return switch (candidate.getPlaceGroup()) {
            case "FOOD_AND_DRINK" -> "restaurant";
            case "ACCOMMODATION" -> "hotel";
            case "SHOPPING_AND_MARKET" -> "shopping";
            case "NATURE_AND_OUTDOORS" -> "nature";
            case "CULTURE_AND_HERITAGE" -> "museum";
            case "ATTRACTIONS" -> "attraction";
            default -> "attraction";
        };
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(normalizeKey(token))) {
                return true;
            }
        }
        return false;
    }

    private TransportMode recommendTransport(AiTripCandidateResponse previous, AiTripCandidateResponse current) {
        if (previous == null || previous.getLat() == null || previous.getLng() == null
                || current.getLat() == null || current.getLng() == null) {
            return null;
        }
        double km = haversineKm(
                previous.getLat().doubleValue(),
                previous.getLng().doubleValue(),
                current.getLat().doubleValue(),
                current.getLng().doubleValue()
        );
        if (km < 1.2) return TransportMode.WALKING;
        if (km < 8.0) return TransportMode.MOTORBIKE;
        if (km < 45.0) return TransportMode.CAR;
        return TransportMode.TRAIN;
    }

    private Map<String, String> generateVisitTips(AiTripDraft draft, List<ScheduledCandidate> schedule) {
        if (schedule.isEmpty()) {
            return Map.of();
        }

        List<Map<String, Object>> payload = new ArrayList<>();
        for (ScheduledCandidate item : schedule) {
            AiTripCandidateResponse candidate = item.candidate();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", candidate.getId());
            row.put("name", candidate.getName());
            row.put("placeGroup", candidate.getPlaceGroup());
            row.put("category", candidate.getCategory());
            row.put("sourceType", candidate.getSourceType());
            row.put("description", trimText(candidate.getDescription(), 120));
            row.put("address", candidate.getAddress());
            row.put("rating", candidate.getRating());
            row.put("dayNumber", item.dayNumber());
            row.put("startTime", item.startTime().toString());
            row.put("endTime", item.endTime().toString());
            row.put("transportModeToHere", item.transportMode() != null ? item.transportMode().name() : null);
            payload.add(row);
        }

        String system = AI_TRIP_SYSTEM_CONTEXT + """
                You write very short practical visit tips for a travel itinerary.
                Return strict JSON only: {"tips":{"candidateId":"short tip text"}}.
                Each tip: 1 short sentence (max 160 chars) on HOW to visit or enjoy the place.
                Use the scheduled time, place type/category, likely weather/season from city and trip dates, and transport context.
                Do NOT copy marketing or listing descriptions. Use only candidate IDs from the input.
                """ + "\n" + AiTripLanguageSupport.aiLanguageRule();
        String prompt = """
                City: %s
                Trip dates: %s to %s
                Pace: %s

                User preferences: %s

                Climate context: infer from city location and month.
                Vietnam north (lat>20): Nov-Mar is cool/dry, Apr-Oct is warm/rainy.
                Vietnam central (lat 12-20): Sep-Dec is rainy/flood risk, Jan-Aug is dry/hot.
                Vietnam south (lat<12): Nov-Apr is dry, May-Oct is rainy.
                Highlands (Sa Pa, Da Lat): always 5-10 C cooler than coastal cities at same latitude.

                Write one practical tip per activity. Rules:
                - Max 120 characters per tip.
                - Be specific to the place - use name, category, and description as context.
                - Focus on HOW to visit: best time to arrive, what to bring, what to avoid, local tip.
                - Use scheduled startTime to make timing-relevant tips (e.g. "arrive early before crowds" for an 08:00 slot).
                - For FOOD_AND_DRINK: mention a dish or ordering tip if inferable from description.
                - For NATURE_AND_OUTDOORS: mention weather/clothing/sun protection relevant to the trip month.
                - For ACCOMMODATION: mention check-in tip or nearby amenity.
                - Never use generic phrases like "enjoy your visit" or "have a great time".
                - Write in the same language as the user preferences field. If preferences is "none", use English.

                Return JSON only:
                {"tips":{"candidateId":"tip text"}}
                Activities:
                %s
                """.formatted(
                draft.getCityName(),
                draft.getStartDate(), draft.getEndDate(),
                draft.getPace(),
                Optional.ofNullable(draft.getPreferenceText()).orElse("none"),
                JsonUtils.toJson(payload));

        Map<String, String> tips = aiClient.completeJson(system, prompt)
                .map(this::parseVisitTips)
                .orElseGet(Map::of);

        Map<String, String> resolved = new HashMap<>();
        for (ScheduledCandidate item : schedule) {
            AiTripCandidateResponse candidate = item.candidate();
            String tip = tips.get(candidate.getId());
            if (tip == null || tip.isBlank()) {
                tip = fallbackVisitTip(candidate, item);
            }
            resolved.put(candidate.getId(), trimText(tip, 280));
        }
        return resolved;
    }

    private Map<String, String> parseVisitTips(String rawJson) {
        try {
            JsonNode tipsNode = objectMapper.readTree(extractJson(rawJson)).get("tips");
            if (tipsNode == null || !tipsNode.isObject()) {
                return Map.of();
            }
            Map<String, String> tips = new HashMap<>();
            tipsNode.fields().forEachRemaining(entry -> {
                String value = entry.getValue().asText("").trim();
                if (!value.isBlank()) {
                    tips.put(entry.getKey(), value);
                }
            });
            return tips;
        } catch (Exception e) {
            log.warn("Failed to parse AI visit tips: {}", e.getMessage());
            return Map.of();
        }
    }

    private String fallbackVisitTip(AiTripCandidateResponse candidate, ScheduledCandidate item) {
        if (candidate.getAiReason() != null && !candidate.getAiReason().isBlank()) {
            return trimText(candidate.getAiReason(), 200);
        }
        String group = candidate.getPlaceGroup() != null ? candidate.getPlaceGroup() : PlaceGroup.OTHER.name();
        String lang = AiTripLanguageSupport.currentCode();
        String timeHint = AiTripLanguageSupport.timeHint(item.startTime().getHour(), lang);
        return AiTripLanguageSupport.fallbackVisitTip(
                group,
                candidate.getSourceType(),
                timeHint);
    }

    private String buildGenerationSummary(AiTripDraft draft,
                                          int selectedCount,
                                          List<ScheduledCandidate> schedule,
                                          List<AiTripCandidateResponse> skipped,
                                          int filledDays) {
        return AiTripGenerationSummary.build(
                draft,
                selectedCount,
                schedule.size(),
                skipped,
                filledDays);
    }

    private AiTripConfirmResponse completedResponse(AiTripDraft draft) {
        TripResponse trip = TripResponse.builder()
                .id(draft.getCreatedTripId())
                .name(draft.getTripName())
                .destination(draft.getCityName())
                .lat(draft.getCityLat())
                .lng(draft.getCityLng())
                .startDate(draft.getStartDate())
                .endDate(draft.getEndDate())
                .build();
        return AiTripConfirmResponse.builder()
                .trip(trip)
                .selectedCount(0)
                .scheduledCount(0)
                .filledDays(0)
                .totalDays(draft.getDayCount())
                .coverageMessage(AiTripLanguageSupport.tripAlreadyCreatedMessage())
                .build();
    }

    private List<AiTripCandidateResponse> parseCandidates(String json) {
        List<AiTripCandidateResponse> candidates = JsonUtils.fromJson(json,
                new TypeReference<List<AiTripCandidateResponse>>() {
                });
        return candidates != null ? candidates : List.of();
    }

    private String buildCoverageMessage(int selectedCount, int scheduledCount, int filledDays, int totalDays) {
        return AiTripLanguageSupport.coverageMessage(selectedCount, scheduledCount, filledDays, totalDays);
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return raw.substring(start, end + 1);
        }
        return raw;
    }

    private String trimText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 3) + "...";
    }

    private String formatDuration(Integer minutes) {
        if (minutes == null || minutes <= 0) return null;
        int hours = minutes / 60;
        int mins = minutes % 60;
        if (hours == 0) return mins + "m";
        if (mins == 0) return hours + "h";
        return hours + "h " + mins + "m";
    }

    private String firstNonBlank(String first, String second) {
        return first != null && !first.isBlank() ? first : second;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String normalizeKey(String value) {
        if (value == null) return "";
        String noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private double distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return Double.MAX_VALUE / 4;
        }
        double r = 6371;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                * Math.cos(Math.toRadians(lat2.doubleValue()))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private record ScheduledCandidate(AiTripCandidateResponse candidate, int dayNumber,
                                      LocalTime startTime, LocalTime endTime,
                                      TransportMode transportMode) {
    }

    // Context builder helper methods
    private String buildBudgetContext(AiTripGenerateRequest request) {
        if (request.getBudgetMin() == null && request.getBudgetMax() == null) {
            return "";
        }
        String currency = request.getBudgetCurrency() != null ? request.getBudgetCurrency() : "VND";
        if (request.getBudgetMin() != null && request.getBudgetMax() != null) {
            return String.format("Budget range: %s - %s %s per day", 
                request.getBudgetMin(), request.getBudgetMax(), currency);
        } else if (request.getBudgetMax() != null) {
            return String.format("Budget limit: %s %s per day", request.getBudgetMax(), currency);
        }
        return "";
    }

    private String buildGroupContext(AiTripGenerateRequest request) {
        if (request.getGroupComposition() == null || request.getGroupComposition().isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Group composition: " + request.getGroupComposition());
        if (request.getMobilityConsiderations() != null && !request.getMobilityConsiderations().isEmpty()) {
            sb.append("\nMobility considerations: ").append(String.join(", ", request.getMobilityConsiderations()));
        }
        return sb.toString();
    }

    private String buildDietaryContext(AiTripGenerateRequest request) {
        if (request.getDietaryRestrictions() == null || request.getDietaryRestrictions().isEmpty()) {
            return "";
        }
        return "Dietary restrictions: " + String.join(", ", request.getDietaryRestrictions());
    }

    private String buildActivityTypeContext(AiTripGenerateRequest request) {
        if (request.getActivityTypes() == null || request.getActivityTypes().isEmpty()) {
            return "";
        }
        return "Preferred activity types: " + String.join(", ", request.getActivityTypes());
    }

    private String buildBudgetContextFromDraft(AiTripDraft draft) {
        if (draft.getBudgetMin() == null && draft.getBudgetMax() == null) {
            return "";
        }
        String currency = draft.getBudgetCurrency() != null ? draft.getBudgetCurrency() : "VND";
        if (draft.getBudgetMin() != null && draft.getBudgetMax() != null) {
            return String.format("Budget range: %s - %s %s per day", 
                draft.getBudgetMin(), draft.getBudgetMax(), currency);
        } else if (draft.getBudgetMax() != null) {
            return String.format("Budget limit: %s %s per day", draft.getBudgetMax(), currency);
        }
        return "";
    }

    private String buildGroupContextFromDraft(AiTripDraft draft) {
        if (draft.getGroupComposition() == null || draft.getGroupComposition().isBlank()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("Group composition: " + draft.getGroupComposition());
        if (draft.getMobilityConsiderations() != null && !draft.getMobilityConsiderations().isBlank()) {
            try {
                List<String> considerations = JsonUtils.fromJson(draft.getMobilityConsiderations(), 
                    new TypeReference<List<String>>() {});
                if (considerations != null && !considerations.isEmpty()) {
                    sb.append("\nMobility considerations: ").append(String.join(", ", considerations));
                }
            } catch (Exception e) {
                log.warn("Failed to parse mobility considerations: {}", e.getMessage());
            }
        }
        return sb.toString();
    }

    private String buildDietaryContextFromDraft(AiTripDraft draft) {
        if (draft.getDietaryRestrictions() == null || draft.getDietaryRestrictions().isBlank()) {
            return "";
        }
        try {
            List<String> restrictions = JsonUtils.fromJson(draft.getDietaryRestrictions(), 
                new TypeReference<List<String>>() {});
            if (restrictions != null && !restrictions.isEmpty()) {
                return "Dietary restrictions: " + String.join(", ", restrictions);
            }
        } catch (Exception e) {
            log.warn("Failed to parse dietary restrictions: {}", e.getMessage());
        }
        return "";
    }

    private String buildActivityTypeContextFromDraft(AiTripDraft draft) {
        if (draft.getActivityTypes() == null || draft.getActivityTypes().isBlank()) {
            return "";
        }
        try {
            List<String> types = JsonUtils.fromJson(draft.getActivityTypes(), 
                new TypeReference<List<String>>() {});
            if (types != null && !types.isEmpty()) {
                return "Preferred activity types: " + String.join(", ", types);
            }
        } catch (Exception e) {
            log.warn("Failed to parse activity types: {}", e.getMessage());
        }
        return "";
    }
}
