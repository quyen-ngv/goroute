package com.ds.goroute.utils.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.util.UUID;

/**
 * Reads a UUID and turns anything else into {@code null} instead of rejecting the request.
 * For optional references that clients fill from mixed sources, such as a trip destination
 * picked from map search, whose ID is a Goong or Mapbox place ID rather than one of ours.
 */
public class LenientUuidDeserializer extends JsonDeserializer<UUID> {

    @Override
    public UUID deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException notAUuid) {
            return null;
        }
    }
}
