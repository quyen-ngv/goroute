package com.ds.goroute.service;

import com.ds.goroute.dto.request.AiTripCandidateResolutionRequest;
import com.ds.goroute.entity.AiTripGenerationJob;
import com.ds.goroute.mapper.AiTripGenerationMapper;
import com.ds.goroute.repository.ActivityRepository;
import com.ds.goroute.repository.AppConfigRepository;
import com.ds.goroute.repository.PlaceRepository;
import com.ds.goroute.service.placelookup.GoongPlaceLookupProvider;
import com.ds.goroute.service.placelookup.PlaceHit;
import com.ds.goroute.service.placelookup.PlaceLookupProvider;
import com.ds.goroute.service.placelookup.PlaceLookupProviderType;
import com.ds.goroute.service.placelookup.PlaceLookupQuery;
import com.ds.goroute.service.placelookup.PlaceLookupProviders;
import com.ds.goroute.thirdparty.goong.GoongClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how AI-trip Web candidates are resolved through Goong, so moving the Goong calls
 * behind a provider cannot change the rows the AI worker receives.
 */
class AiTripGenerationServiceGoongResolutionTest {

    private static final String AUTOCOMPLETE = "/Place/AutoComplete";
    private static final String DETAIL = "/Place/Detail";
    private static final BigDecimal CENTER_LAT = new BigDecimal("10.7769");
    private static final BigDecimal CENTER_LNG = new BigDecimal("106.7009");

    private GoongClient goongClient;
    private AiTripGenerationService service;
    private AiTripGenerationMapper mapper;
    private ObjectMapper objectMapper;
    private UUID jobId;

    @BeforeEach
    void setUp() {
        mapper = mock(AiTripGenerationMapper.class);
        goongClient = mock(GoongClient.class);
        objectMapper = new ObjectMapper();
        service = new AiTripGenerationService(
                mapper,
                mock(AiTripQuotaService.class),
                mock(AiCreditService.class),
                mock(PlaceRepository.class),
                mock(TripService.class),
                mock(ActivityRepository.class),
                objectMapper,
                mock(AiTripSseService.class),
                mock(AppConfigRepository.class),
                mock(NotificationService.class),
                new PlaceLookupProviders(List.of(new GoongPlaceLookupProvider(goongClient, objectMapper))));
        jobId = UUID.randomUUID();
        AiTripGenerationJob job = new AiTripGenerationJob();
        job.setId(jobId);
        job.setAttemptId("attempt-1");
        job.setLocale("vi");
        when(mapper.findById(jobId)).thenReturn(job);
    }

    @Test
    void resolvesTheFirstPredictionWhoseNameAndDetailMatchExactly() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any())).thenReturn(ResponseEntity.ok("""
                {"predictions":[
                  {"place_id":"goong-other","structured_formatting":{"main_text":"Cong Caphe Bis"}},
                  {"place_id":"goong-1","structured_formatting":{"main_text":"Cộng Cà Phê"}}
                ]}"""));
        when(goongClient.forward(eq(DETAIL), any())).thenReturn(ResponseEntity.ok("""
                {"result":{"name":"Cong Ca Phe","formatted_address":"26 Ly Tu Trong",
                  "geometry":{"location":{"lat":10.7772,"lng":106.7012}},
                  "types":["cafe","food"]}}"""));

        Map<String, Object> row = resolveOne("Cộng cà phê");

        assertThat(row).containsExactly(
                entry("googlePlaceId", "goong-1"),
                entry("title", "Cong Ca Phe"),
                entry("address", "26 Ly Tu Trong"),
                entry("latitude", new BigDecimal("10.7772")),
                entry("longitude", new BigDecimal("106.7012")),
                entry("placeGroup", "FOOD"),
                entry("category", "cafe"),
                entry("resolvedBy", "GOONG"),
                entry("resolutionStatus", "RESOLVED"),
                entry("externalResolutionVerified", true),
                entry("candidateId", "c1"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> autocompleteParams = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(goongClient).forward(eq(AUTOCOMPLETE), autocompleteParams.capture());
        assertThat(autocompleteParams.getValue().toSingleValueMap()).containsExactly(
                entry("input", "Cộng cà phê"),
                entry("location", "10.7769,106.7009"),
                entry("radius", "50000"),
                entry("limit", "5"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<MultiValueMap<String, String>> detailParams = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(goongClient).forward(eq(DETAIL), detailParams.capture());
        assertThat(detailParams.getValue().toSingleValueMap()).containsExactly(entry("place_id", "goong-1"));
    }

    @Test
    void fallsBackToThePredictionNameAndLeavesMissingFieldsNull() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any())).thenReturn(ResponseEntity.ok("""
                {"predictions":[{"place_id":"goong-2","name":"Ben Thanh Market"}]}"""));
        when(goongClient.forward(eq(DETAIL), any())).thenReturn(ResponseEntity.ok("""
                {"result":{"name":"Ben Thanh Market","geometry":{"location":{"lat":10.772,"lng":106.698}},"types":[]}}"""));

        Map<String, Object> row = resolveOne("Ben Thanh Market");

        assertThat(row).containsEntry("googlePlaceId", "goong-2")
                .containsEntry("address", null)
                .containsEntry("category", null)
                .containsEntry("resolutionStatus", "RESOLVED");
    }

    @Test
    void triesTheNextPredictionWhenADetailCallFails() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any())).thenReturn(ResponseEntity.ok("""
                {"predictions":[
                  {"place_id":"goong-a","structured_formatting":{"main_text":"Pho Hoa"}},
                  {"place_id":"goong-b","structured_formatting":{"main_text":"Pho Hoa"}}
                ]}"""));
        when(goongClient.forward(eq(DETAIL), any())).thenAnswer(invocation -> {
            MultiValueMap<String, String> params = invocation.getArgument(1);
            if ("goong-a".equals(params.getFirst("place_id"))) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("{}");
            }
            return ResponseEntity.ok("""
                    {"result":{"name":"Pho Hoa","geometry":{"location":{"lat":10.79,"lng":106.69}}}}""");
        });

        Map<String, Object> row = resolveOne("Pho Hoa");

        assertThat(row).containsEntry("googlePlaceId", "goong-b").containsEntry("category", null);
    }

    @Test
    void staysUnresolvedWhenTheDetailNameDiffers() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any())).thenReturn(ResponseEntity.ok("""
                {"predictions":[{"place_id":"goong-1","structured_formatting":{"main_text":"Pho Hoa"}}]}"""));
        when(goongClient.forward(eq(DETAIL), any())).thenReturn(ResponseEntity.ok("""
                {"result":{"name":"Pho Hoa Pasteur","geometry":{"location":{"lat":10.79,"lng":106.69}}}}"""));

        assertUnresolved(resolveOne("Pho Hoa"), "Pho Hoa");
    }

    @Test
    void staysUnresolvedWhenTheMatchIsFartherThanFiftyKilometres() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any())).thenReturn(ResponseEntity.ok("""
                {"predictions":[{"place_id":"goong-1","structured_formatting":{"main_text":"Pho Hoa"}}]}"""));
        when(goongClient.forward(eq(DETAIL), any())).thenReturn(ResponseEntity.ok("""
                {"result":{"name":"Pho Hoa","geometry":{"location":{"lat":21.03,"lng":105.85}}}}"""));

        assertUnresolved(resolveOne("Pho Hoa"), "Pho Hoa");
    }

    @Test
    void staysUnresolvedWithoutDetailCallsWhenAutocompleteFails() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any()))
                .thenReturn(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("{}"));

        assertUnresolved(resolveOne("Pho Hoa"), "Pho Hoa");
        verify(goongClient, never()).forward(eq(DETAIL), any());
    }

    @Test
    void staysUnresolvedWhenGoongAnswersWithSomethingOtherThanJson() {
        when(goongClient.forward(eq(AUTOCOMPLETE), any())).thenReturn(ResponseEntity.ok("not json"));

        assertUnresolved(resolveOne("Pho Hoa"), "Pho Hoa");
    }

    @Test
    void resolvesWithMapboxOutsideVietnamWhereGoongHasNothing() {
        PlaceHit hit = new PlaceHit(PlaceLookupProviderType.MAPBOX, "mapbox-1", "Pike Place Market",
                "85 Pike St, Seattle", new BigDecimal("47.6097"), new BigDecimal("-122.3422"), "market", null);
        PlaceLookupProvider mapbox = new PlaceLookupProvider() {
            public PlaceLookupProviderType type() { return PlaceLookupProviderType.MAPBOX; }
            public boolean isAvailable() { return true; }
            public List<PlaceHit> searchText(PlaceLookupQuery query) { return List.of(hit); }
            public Optional<PlaceHit> getDetails(String id) { return Optional.of(hit); }
        };
        service = new AiTripGenerationService(mapper, mock(AiTripQuotaService.class), mock(AiCreditService.class),
                mock(PlaceRepository.class),
                mock(TripService.class), mock(ActivityRepository.class), objectMapper, mock(AiTripSseService.class),
                mock(AppConfigRepository.class), mock(NotificationService.class),
                new PlaceLookupProviders(List.of(new GoongPlaceLookupProvider(goongClient, objectMapper), mapbox)));

        Map<String, Object> row = resolveOne("Pike Place Market", new BigDecimal("47.6062"), new BigDecimal("-122.3321"));

        assertThat(row).contains(entry("googlePlaceId", "mapbox-1"), entry("address", "85 Pike St, Seattle"),
                entry("resolvedBy", "MAPBOX"), entry("externalResolutionVerified", true));
        verify(goongClient, never()).forward(any(), any());
    }

    private Map<String, Object> resolveOne(String title) {
        return resolveOne(title, CENTER_LAT, CENTER_LNG);
    }

    private Map<String, Object> resolveOne(String title, BigDecimal latitude, BigDecimal longitude) {
        AiTripCandidateResolutionRequest.Candidate candidate = new AiTripCandidateResolutionRequest.Candidate();
        candidate.setCandidateId("c1");
        candidate.setTitle(title);
        candidate.setPlaceGroup("FOOD");
        AiTripCandidateResolutionRequest request = new AiTripCandidateResolutionRequest();
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        request.setCandidates(List.of(candidate));
        List<Map<String, Object>> rows = service.resolveCandidates(jobId, "attempt-1", request);
        assertThat(rows).hasSize(1);
        return rows.get(0);
    }

    private void assertUnresolved(Map<String, Object> row, String title) {
        assertThat(row).containsExactly(
                entry("title", title),
                entry("placeGroup", "FOOD"),
                entry("resolutionStatus", "UNRESOLVED"),
                entry("externalResolutionVerified", false),
                entry("candidateId", "c1"));
    }
}
