package com.ds.goroute.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * Request bodies may carry {@code LocalDateTime} values with an offset ({@code ...Z},
 * {@code ...+07:00}). Those are instants: convert them to the UTC wall clock every
 * {@code TIMESTAMP} column holds. Values without an offset keep the old behaviour and are
 * taken as already-UTC, which is what the app's response interceptor assumes too.
 */
@Configuration
public class TimeJacksonConfig {

    @Bean
    Jackson2ObjectMapperBuilderCustomizer utcLocalDateTimeCustomizer() {
        SimpleModule module = new SimpleModule("utc-local-date-time");
        module.addDeserializer(LocalDateTime.class, new UtcLocalDateTimeDeserializer());
        // postConfigurer runs after JavaTimeModule, so this deserializer takes precedence.
        return builder -> builder.postConfigurer(mapper -> mapper.registerModule(module));
    }

    static final class UtcLocalDateTimeDeserializer extends LocalDateTimeDeserializer {

        private static final Pattern WITH_OFFSET =
                Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T.+(?:Z|z|[+-]\\d{2}:?\\d{2})$");

        UtcLocalDateTimeDeserializer() {
            super(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (parser.hasToken(JsonToken.VALUE_STRING)) {
                LocalDateTime utc = toUtc(parser.getText());
                if (utc != null) {
                    return utc;
                }
            }
            return super.deserialize(parser, context);
        }

        static LocalDateTime toUtc(String raw) {
            if (raw == null) {
                return null;
            }
            String value = raw.trim();
            if (!WITH_OFFSET.matcher(value).matches()) {
                return null;
            }
            try {
                return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .withOffsetSameInstant(ZoneOffset.UTC)
                        .toLocalDateTime();
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
