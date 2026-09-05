package com.ds.goroute.service;

import com.ds.goroute.entity.SocialLocationJob;
import com.ds.goroute.type.BusinessConfigKey;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SocialItineraryRequestFactoryTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BusinessConfigService config = mock(BusinessConfigService.class);
    private final SocialItineraryRequestFactory factory = new SocialItineraryRequestFactory(config);

    @Test
    void preservesSourceOrderUnresolvedRowsAndSeparateOptions() throws Exception {
        when(config.getInt(BusinessConfigKey.SOCIAL_START_OFFSET_DAYS)).thenReturn(5);
        UUID jobId = UUID.randomUUID();
        SocialLocationJob job = SocialLocationJob.builder()
                .id(jobId)
                .sourceUrl("https://www.tiktok.com/@creator/video/123")
                .videoDurationSeconds(42)
                .build();
        JsonNode result = objectMapper.readTree("""
                {"extraction":{
                  "contentType":"ITINERARY",
                  "summary":"Three days in Da Nang",
                  "durationDays":3,
                  "destination":{"name":"Da Nang","city":"Da Nang"},
                  "candidates":[
                    {"candidateRef":"social-0001","sequence":1,"name":"Dragon Bridge","query":"Dragon Bridge","description":"Night view","resolutionStatus":"UNRESOLVED","optionGroupId":"d1-lunch","optionIndex":1,"relation":"OPTION"},
                    {"candidateRef":"social-0002","sequence":2,"name":"Bun cha shop","query":"Bun cha shop","resolutionStatus":"RESOLVED","optionGroupId":"d1-lunch","optionIndex":2,"relation":"OPTION","mapSearch":{"candidates":[{"placeId":"google-1","latitude":16.05,"longitude":108.2,"placeMapping":{"placeId":"00000000-0000-0000-0000-000000000001"}}]}}
                  ]
                }}
                """);

        var request = factory.build(job, result);

        assertThat(request).isNotNull();
        assertThat(request.getStartDate()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(request.getEndDate()).isEqualTo(request.getStartDate().plusDays(2));
        assertThat(request.getCityName()).isEqualTo("Da Nang");
        assertThat(request.getSocialContext()).containsEntry("socialJobId", jobId.toString());
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) request.getSocialContext().get("candidates");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("candidateId", "social-0001")
                .containsEntry("resolutionStatus", "UNRESOLVED");
        assertThat(rows.get(1)).containsEntry("candidateId", "social-0002")
                .containsEntry("optionGroupId", "d1-lunch")
                .containsEntry("optionIndex", 2)
                .containsEntry("id", "00000000-0000-0000-0000-000000000001");
    }

    @Test
    void returnsNullForPlaceListBecauseItMustNotCreateASchedule() throws Exception {
        when(config.getInt(BusinessConfigKey.SOCIAL_START_OFFSET_DAYS)).thenReturn(5);
        SocialLocationJob job = SocialLocationJob.builder().id(UUID.randomUUID()).build();

        var request = factory.build(job, objectMapper.readTree("""
                {"extraction":{"contentType":"PLACE_LIST","candidates":[{"name":"A"}]}}
                """));

        assertThat(request).isNull();
    }

    @Test
    void doesNotInventDurationWhenItineraryExtractorOmitsIt() throws Exception {
        when(config.getInt(BusinessConfigKey.SOCIAL_START_OFFSET_DAYS)).thenReturn(5);
        SocialLocationJob job = SocialLocationJob.builder().id(UUID.randomUUID()).build();

        var request = factory.build(job, objectMapper.readTree("""
                {"extraction":{"contentType":"ITINERARY","destination":{"city":"Hue"},
                  "candidates":[{"candidateRef":"1","sequence":1,"name":"A"},{"candidateRef":"2","sequence":2,"name":"B"},{"candidateRef":"3","sequence":3,"name":"C"},{"candidateRef":"4","sequence":4,"name":"D"},{"candidateRef":"5","sequence":5,"name":"E"},{"candidateRef":"6","sequence":6,"name":"F"}]}}
                """));

        assertThat(request).isNull();
    }

    @Test
    void usesVideoDayHintsWhenTheExtractorProvidesThoseButNoDurationField() throws Exception {
        when(config.getInt(BusinessConfigKey.SOCIAL_START_OFFSET_DAYS)).thenReturn(5);
        SocialLocationJob job = SocialLocationJob.builder().id(UUID.randomUUID()).build();

        var request = factory.build(job, objectMapper.readTree("""
                {"extraction":{"contentType":"ITINERARY","destination":{"city":"Hue"},
                  "candidates":[{"candidateRef":"1","sequence":1,"dayHint":1,"name":"A"},{"candidateRef":"2","sequence":2,"dayHint":2,"name":"B"}]}}
                """));

        assertThat(request).isNotNull();
        assertThat(request.getDayCount()).isEqualTo(2);
        assertThat(request.getEndDate()).isEqualTo(request.getStartDate().plusDays(1));
    }

    @Test
    void assignsSourceSequenceWhenExtractorOmitsIt() throws Exception {
        when(config.getInt(BusinessConfigKey.SOCIAL_START_OFFSET_DAYS)).thenReturn(5);
        SocialLocationJob job = SocialLocationJob.builder().id(UUID.randomUUID()).build();

        var request = factory.build(job, objectMapper.readTree("""
                {"extraction":{"contentType":"ITINERARY","durationDays":1,
                  "destination":{"city":"Da Nang"},
                  "candidates":[
                    {"candidateRef":"first","name":"First stop"},
                    {"candidateRef":"second","name":"Second stop"}]}}
                """));

        assertThat(request).isNotNull();
        @SuppressWarnings("unchecked")
        var rows = (java.util.List<Map<String, Object>>) request.getSocialContext().get("candidates");
        assertThat(rows).extracting(row -> row.get("socialSequence"))
                .containsExactly(1, 2);
    }
}
