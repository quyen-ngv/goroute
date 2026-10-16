package com.ds.goroute.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class TimeJacksonConfigTest {

    record Body(LocalDateTime at) {
    }

    private final ObjectMapper mapper = mapper();

    private static ObjectMapper mapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new TimeJacksonConfig().utcLocalDateTimeCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void anOffsetIsAnInstantAndLandsAsTheUtcWallClock() throws Exception {
        assertThat(mapper.readValue("{\"at\":\"2026-10-14T10:00:00+07:00\"}", Body.class).at())
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 3, 0));
        assertThat(mapper.readValue("{\"at\":\"2026-10-14T03:00:00.000Z\"}", Body.class).at())
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 3, 0));
        assertThat(mapper.readValue("{\"at\":\"2026-10-13T20:00:00-07:00\"}", Body.class).at())
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 3, 0));
    }

    @Test
    void aValueWithoutOffsetIsKeptAsItCame() throws Exception {
        assertThat(mapper.readValue("{\"at\":\"2026-10-14T10:00:00\"}", Body.class).at())
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 10, 0));
        assertThat(mapper.readValue("{\"at\":\"2026-10-14T10:00:00.123\"}", Body.class).at())
                .isEqualTo(LocalDateTime.of(2026, 10, 14, 10, 0, 0, 123_000_000));
    }
}
