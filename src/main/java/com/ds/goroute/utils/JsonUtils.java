package com.ds.goroute.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.List;

public class JsonUtils {

    /**
     * Shared mapper. JavaTimeModule is mandatory: without it Jackson refuses to serialize
     * java.time fields (LocalDateTime on entities), which silently turned into an empty
     * string and broke jsonb inserts.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    private JsonUtils() {
    }

    public static <T> T getGenericObject(Object input, Class<T> clazz) {
        try {
            return MAPPER.convertValue(input, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T getEntityFromJsonStr(String input, Class<T> clazz) {
        try {
            return MAPPER.readValue(input, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    public static String convertObjectToString(Object obj) throws JsonProcessingException {
        return MAPPER.writeValueAsString(obj);
    }

    public static String toJsonString(Object input) {
        try {
            return MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static <T> T readObject(String input, Class<T> clazz) {
        try {
            return MAPPER.readValue(input, clazz);
        } catch (Exception ex) {
            return null;
        }
    }

    // Thêm method mới cho TypeReference
    public static <T> T readObject(String input, TypeReference<T> typeReference) {
        try {
            return MAPPER.readValue(input, typeReference);
        } catch (Exception ex) {
            return null;
        }
    }

    public static <T> T readListObject(Object input, Class<T> clazz) throws IOException {
        return MAPPER.readValue(MAPPER.writeValueAsString(input),
                MAPPER.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    // Add methods for review system
    public static String toJson(Object obj) {
        return toJsonString(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return readObject(json, clazz);
    }

    public static <T> T fromJson(String json, TypeReference<T> typeReference) {
        return readObject(json, typeReference);
    }
}
