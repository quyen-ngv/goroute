package com.ds.goroute.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AiTripDestinationRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void mapSearchPlaceIdIsReadAsNoLocationImage() throws Exception {
        AiTripDestinationRequest destination = objectMapper.readValue("""
                {"locationImageId":"dXJuOm1ieHBsYzpBUWxL","name":"Seattle, Washington, United States",
                 "latitude":47.6062,"longitude":-122.3321,"startDate":"2026-11-01","endDate":"2026-11-03"}""",
                AiTripDestinationRequest.class);

        assertThat(destination.getLocationImageId()).isNull();
        assertThat(destination.getName()).isEqualTo("Seattle, Washington, United States");
    }

    @Test
    void locationImageUuidIsKept() throws Exception {
        UUID id = UUID.randomUUID();
        AiTripDestinationRequest destination = objectMapper.readValue(
                "{\"locationImageId\":\"" + id + "\"}", AiTripDestinationRequest.class);

        assertThat(destination.getLocationImageId()).isEqualTo(id);
    }
}
