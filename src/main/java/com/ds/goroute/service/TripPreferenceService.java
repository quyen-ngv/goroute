package com.ds.goroute.service;

import com.ds.goroute.dto.request.AiTripGenerateRequest;
import com.ds.goroute.dto.request.TripPreferencesRequest;
import com.ds.goroute.entity.TripPreference;
import com.ds.goroute.mapper.TripPreferenceMapper;
import com.ds.goroute.type.PlaceGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores what the traveller answered while creating a trip, in a shape made for filtering
 * (see V208). Called inside trip creation; preferences are optional, so a null request is a
 * no-op rather than an empty row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TripPreferenceService {
    private static final Pattern CODE = Pattern.compile("[A-Z_]{1,40}");
    private static final Set<String> COMPANIONS = Set.of("SOLO", "COUPLE", "FAMILY", "FRIENDS");
    private static final Set<String> PACES = Set.of("RELAXED", "BALANCED", "EAGER");
    private static final Set<String> DISCOVERY = Set.of("FAMOUS", "BALANCED", "HIDDEN_GEMS");

    private final TripPreferenceMapper mapper;

    public void save(UUID tripId, TripPreferencesRequest request, String source) {
        if (tripId == null || request == null) return;
        TripPreference preference = TripPreference.builder()
                .tripId(tripId)
                .source(source)
                .companion(oneOf(request.getCompanion(), COMPANIONS))
                .withChildren(Boolean.TRUE.equals(request.getWithChildren()))
                .withElderly(Boolean.TRUE.equals(request.getWithElderly()))
                .pace(oneOf(request.getPace(), PACES))
                .discoveryStyle(oneOf(request.getDiscoveryStyle(), DISCOVERY))
                .dietaryNote(trimToNull(request.getDietaryNote(), 120))
                .preferenceText(trimToNull(request.getPreferenceText(), 2000))
                .build();
        mapper.upsert(preference);
        mapper.deleteTags(tripId);
        insertTags(tripId, TripPreference.TAG_INTEREST, codes(request.getInterests()));
        insertTags(tripId, TripPreference.TAG_DIETARY, codes(request.getDietary()));
    }

    /**
     * Preferences of an AI trip. The app sends them structured in {@code preferences}; requests
     * from older app versions (and social-video adapters) only carry the prompt fields, so the
     * structured answers are rebuilt from those where they map one to one.
     */
    public TripPreferencesRequest fromAiRequest(AiTripGenerateRequest request) {
        if (request == null) return null;
        if (request.getPreferences() != null) {
            TripPreferencesRequest structured = request.getPreferences();
            if (structured.getPreferenceText() == null) structured.setPreferenceText(request.getPreferenceText());
            return structured;
        }
        if (request.getSocialContext() != null) return null;
        List<String> interests = new ArrayList<>();
        if (request.getPlaceGroups() != null) request.getPlaceGroups().stream().map(PlaceGroup::name).forEach(interests::add);
        if (request.getActivityTypes() != null) interests.addAll(request.getActivityTypes());
        List<String> mobility = request.getMobilityConsiderations() == null ? List.of() : request.getMobilityConsiderations();
        return TripPreferencesRequest.builder()
                .pace(request.getPace())
                .discoveryStyle(request.getDiscoveryStyle())
                .withChildren(mobility.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("kid")))
                .withElderly(mobility.stream().anyMatch(m -> m.toLowerCase(Locale.ROOT).contains("elderly")))
                .interests(interests)
                .dietary(request.getDietaryRestrictions())
                .preferenceText(request.getPreferenceText())
                .build();
    }

    private void insertTags(UUID tripId, String kind, List<String> values) {
        if (!values.isEmpty()) mapper.insertTags(tripId, kind, values);
    }

    /** Upper-case codes, invalid or free-text entries dropped, duplicates removed, order kept. */
    static List<String> codes(List<String> raw) {
        if (raw == null) return List.of();
        Set<String> out = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null) continue;
            String code = value.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
            if (CODE.matcher(code).matches()) out.add(code);
        }
        return List.copyOf(out);
    }

    private static String oneOf(String value, Set<String> allowed) {
        if (value == null) return null;
        String code = value.trim().toUpperCase(Locale.ROOT);
        return allowed.contains(code) ? code : null;
    }

    private static String trimToNull(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > max ? trimmed.substring(0, max) : trimmed;
    }
}
