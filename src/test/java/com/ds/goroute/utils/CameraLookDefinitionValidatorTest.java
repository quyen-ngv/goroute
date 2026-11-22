package com.ds.goroute.utils;

import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.type.CameraLookKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CameraLookDefinitionValidatorTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OWN = "https://cdn.example/camera-looks/a.png";

    private static void validate(CameraLookKind kind, String json) throws Exception {
        JsonNode definition = MAPPER.readTree(json);
        CameraLookDefinitionValidator.validate(kind, definition, url -> url.startsWith("https://cdn.example/camera-looks/"));
    }

    @Test
    @DisplayName("Should accept a filter with adjust, LUT, strength and effects")
    void acceptsFullFilter() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FILTER, """
                {"adjust":{"brightness":0.02,"contrast":1.08,"saturation":1.15,"warmth":-0.05,"tint":-0.02,"fade":0.1},
                 "lut":"%s","strength":0.8,"effects":{"grain":0.03,"vignette":0.12}}
                """.formatted(OWN)));
    }

    @Test
    @DisplayName("Should accept a filter that has only a 20-number matrix")
    void acceptsMatrixOnlyFilter() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FILTER,
                "{\"matrix\":[1,0,0,0,0, 0,1,0,0,0, 0,0,1,0,0, 0,0,0,1,0]}"));
    }

    @Test
    @DisplayName("Should accept the plain filter, and a shelf and an icon on a filter")
    void acceptsPlainAndShelvedFilter() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FILTER, "{}"));
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FILTER,
                "{\"adjust\":{},\"group\":\"stock\",\"icon\":\"sun\"}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"adjust\":{},\"group\":\"Film Stock\"}",
            // A LUT alone has no fallback for devices where the shader cannot run.
            "{\"lut\":\"https://cdn.example/camera-looks/a.png\"}",
            "{\"adjust\":{\"contrast\":3.5}}",
            "{\"adjust\":{\"sharpness\":1}}",
            "{\"adjust\":{},\"matrix\":[1,2,3]}",
            "{\"adjust\":{},\"lut\":\"https://elsewhere.example/a.png\"}",
            "{\"adjust\":{},\"strength\":2}",
            "{\"adjust\":{},\"effects\":{\"blur\":0.2}}",
            "{\"adjust\":{},\"unknown\":true}"
    })
    @DisplayName("Should reject a malformed filter")
    void rejectsBadFilter(String json) {
        assertThatThrownBy(() -> validate(CameraLookKind.FILTER, json)).isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("Should accept a frame with images, a photo rectangle and text slots")
    void acceptsFullFrame() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FRAME, """
                {"images":{"threeTwo":"%s","square":"%s"},
                 "photoRect":{"threeTwo":[0.05,0.05,0.9,0.75]},
                 "texts":{"square":[{"source":"placeName","x":0.5,"y":0.9,"size":0.05,"color":"#FFFFFF",
                   "weight":600,"align":"center","uppercase":true,"maxWidth":0.8,"rotation":-5},
                   {"source":"date","x":0.5,"y":0.95,"size":0.03,"color":"#CC000000","weight":400,
                   "align":"left","uppercase":false,"maxWidth":0.5,"dateFormat":"dd.MM.yyyy"}]}}
                """.formatted(OWN, OWN)));
    }

    @Test
    @DisplayName("Should accept the plain frame and a text-only frame drawn as a camera")
    void acceptsPlainAndTextOnlyFrame() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FRAME, "{}"));
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FRAME, """
                {"camera":{"body":"mini","color":"#FFC93C","film":"gold"},
                 "texts":{"threeTwo":[{"source":"time","x":0.5,"y":0.08,"size":0.04,"color":"#FFFFFF",
                   "background":"#66000000","weight":600,"align":"center","uppercase":false,"maxWidth":0.6}]}}
                """));
    }

    @Test
    @DisplayName("Should accept a frame with a shape of its own")
    void acceptsCanvasFrame() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.FRAME, """
                {"images":{"threeTwo":"%s"},"canvas":[1620,1296],"photoRect":{"threeTwo":[0.02,0.125,0.96,0.75]},"bulge":0.55}
                """.formatted(OWN)));
    }

    @Test
    @DisplayName("Should accept a film shelf only with an empty definition")
    void shelfHasNoDefinition() {
        assertThatNoException().isThrownBy(() -> validate(CameraLookKind.GROUP, "{}"));
        assertThatThrownBy(() -> validate(CameraLookKind.GROUP, "{\"icon\":\"sun\"}"))
                .isInstanceOf(BusinessException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"canvas\":[0,100]}",
            "{\"bulge\":1.5}",
            "{\"camera\":{\"body\":\"drone\"}}",
            "{\"images\":{\"wide\":\"https://cdn.example/camera-looks/a.png\"}}",
            "{\"images\":{\"square\":\"https://elsewhere.example/a.png\"}}",
            // A rectangle for a shape the frame has no image of.
            "{\"images\":{\"square\":\"https://cdn.example/camera-looks/a.png\"},\"photoRect\":{\"threeTwo\":[0,0,1,1]}}",
            "{\"images\":{\"square\":\"https://cdn.example/camera-looks/a.png\"},\"photoRect\":{\"square\":[0.5,0,0.6,1]}}",
            "{\"images\":{\"square\":\"https://cdn.example/camera-looks/a.png\"},\"texts\":{\"square\":[{\"source\":\"weather\",\"x\":0.5,\"y\":0.5,\"size\":0.05,\"color\":\"#FFFFFF\",\"weight\":600,\"align\":\"center\",\"uppercase\":false,\"maxWidth\":0.8}]}}",
            "{\"images\":{\"square\":\"https://cdn.example/camera-looks/a.png\"},\"texts\":{\"square\":[{\"source\":\"caption\",\"x\":0.5,\"y\":0.5,\"size\":0.05,\"color\":\"white\",\"weight\":600,\"align\":\"center\",\"uppercase\":false,\"maxWidth\":0.8}]}}",
            "{\"images\":{\"square\":\"https://cdn.example/camera-looks/a.png\"},\"texts\":{\"square\":[{\"source\":\"caption\",\"x\":0.5,\"y\":0.5,\"size\":0.05,\"color\":\"#FFFFFF\",\"weight\":450,\"align\":\"center\",\"uppercase\":false,\"maxWidth\":0.8}]}}"
    })
    @DisplayName("Should reject a malformed frame")
    void rejectsBadFrame(String json) {
        assertThatThrownBy(() -> validate(CameraLookKind.FRAME, json)).isInstanceOf(BusinessException.class);
    }
}
