package com.ds.goroute.dto.request;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SenderLocationRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    /** Spring Boot's own mapper ignores unknown fields; the rest is Jackson's default. */
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    private SenderLocationRequest location(String json) throws Exception {
        return mapper.readValue(json, SendMarketplaceMessageRequest.class).getSenderLocation();
    }

    @Test
    void anOlderAppThatSendsNoPositionIsReadAsBefore() throws Exception {
        SendMarketplaceMessageRequest request = mapper.readValue(
                "{\"clientMessageId\":\"c1\",\"messageType\":\"TEXT\",\"content\":\"ăn gì?\",\"attachments\":[]}",
                SendMarketplaceMessageRequest.class);

        assertThat(request.getSenderLocation()).isNull();
        assertThat(request.getContent()).isEqualTo("ăn gì?");
    }

    @Test
    void aPositionIsReadWithItsAccuracy() throws Exception {
        SenderLocationRequest location = location("{\"clientMessageId\":\"c1\",\"senderLocation\":"
                + "{\"latitude\":11.8123456,\"longitude\":108.3,\"accuracyMeters\":12.5}}");

        assertThat(location.getLatitude()).isEqualByComparingTo("11.8123456");
        assertThat(location.getLongitude()).isEqualByComparingTo("108.3");
        assertThat(location.getAccuracyMeters()).isEqualByComparingTo("12.5");
        assertThat(validator.validate(location)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"-90,-180", "90,180", "0,0", "21.0285,105.8542"})
    void positionsOnTheGlobeAreAccepted(String latitude, String longitude) {
        SenderLocationRequest location = new SenderLocationRequest();
        location.setLatitude(new BigDecimal(latitude));
        location.setLongitude(new BigDecimal(longitude));

        assertThat(validator.validate(location)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"90.0001,0,latitude", "-90.0001,0,latitude", "0,180.0001,longitude", "0,-180.0001,longitude",
            "1e400,0,latitude"})
    void positionsOffTheGlobeAreRefused(String latitude, String longitude, String field) {
        SenderLocationRequest location = new SenderLocationRequest();
        location.setLatitude(new BigDecimal(latitude));
        location.setLongitude(new BigDecimal(longitude));

        assertThat(validator.validate(location)).singleElement()
                .satisfies(violation -> assertThat(violation.getPropertyPath().toString()).isEqualTo(field));
    }

    @Test
    void halfAPositionAndANegativeAccuracyAreRefused() throws Exception {
        assertThat(validator.validate(location("{\"senderLocation\":{\"latitude\":11.8}}"))).hasSize(1);
        assertThat(validator.validate(location("{\"senderLocation\":{\"longitude\":108.3}}"))).hasSize(1);
        assertThat(validator.validate(location(
                "{\"senderLocation\":{\"latitude\":11.8,\"longitude\":108.3,\"accuracyMeters\":-1}}"))).hasSize(1);
    }

    @Test
    void notANumberNeverBecomesAPosition() {
        // Bare NaN is not JSON, and the text "NaN" is not a decimal: both fail before validation (HTTP 400).
        assertThatThrownBy(() -> location("{\"senderLocation\":{\"latitude\":NaN,\"longitude\":108.3}}"))
                .isInstanceOf(java.io.IOException.class);
        assertThatThrownBy(() -> location("{\"senderLocation\":{\"latitude\":\"NaN\",\"longitude\":108.3}}"))
                .isInstanceOf(JsonMappingException.class);
        assertThatThrownBy(() -> location("{\"senderLocation\":{\"latitude\":\"Infinity\",\"longitude\":108.3}}"))
                .isInstanceOf(JsonMappingException.class);
    }

    @Test
    void theNestedPositionIsValidatedWithTheMessage() throws Exception {
        assertThat(SendMarketplaceMessageRequest.class.getDeclaredField("senderLocation")
                .isAnnotationPresent(jakarta.validation.Valid.class)).isTrue();
    }
}
