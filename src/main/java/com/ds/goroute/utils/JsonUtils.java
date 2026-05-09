package com.ds.goroute.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

public class JsonUtils {

    private JsonUtils() {
    }

    public static <T> T getGenericObject(Object input, Class<T> clazz) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            return objectMapper.convertValue(input, clazz);
        } catch (Exception e) {
            return null;
        }
    }

    public static <T> T getEntityFromJsonStr(String input, Class<T> clazz) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
            return objectMapper.readValue(input, clazz);
        } catch (IOException e) {
            return null;
        }
    }

    public static String convertObjectToString(Object obj) throws JsonProcessingException {
        return new ObjectMapper().writeValueAsString(obj);
    }

    public static String toJsonString(Object input) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.writeValueAsString(input);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static <T> T readObject(String input, Class<T> clazz) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.readValue(input, clazz);
        } catch (Exception ex) {
            return null;
        }
    }

    // Thêm method mới cho TypeReference
    public static <T> T readObject(String input, TypeReference<T> typeReference) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            return objectMapper.readValue(input, typeReference);
        } catch (Exception ex) {
            return null;
        }
    }

    public static <T> T readListObject(Object input, Class<T> clazz) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper.readValue(objectMapper.writeValueAsString(input),
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
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