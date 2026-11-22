package com.ds.goroute.utils;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessException;
import com.ds.goroute.type.CameraLookKind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The shape of a camera look's {@code definition}, checked on every write. The app reads these
 * objects straight into its renderer, so an unknown key or an out-of-range number is refused
 * here instead of being discovered on a phone.
 *
 * <p>FILTER: {@code adjust} (brightness, contrast, saturation, warmth, tint, fade), {@code matrix}
 * (20 numbers), {@code lut} (URL), {@code strength}, {@code effects} (grain, vignette). A filter
 * with a LUT needs adjust or matrix too: it is what the app draws where the LUT shader cannot
 * run. One with none of the three is the plain look (the photo as taken). {@code group} (the shelf it is listed on) and {@code icon} (the icon on
 * its chip) are names the app looks up; one it does not know falls back to a default.
 *
 * <p>FRAME: {@code images}, {@code photoRect} and {@code texts}, each keyed by shape
 * ({@code threeTwo}, {@code square}). A frame may be text only, and one with neither an image
 * nor a text is the plain frame. {@code canvas} ([width, height]) gives
 * the frame a shape of its own instead of the camera's. {@code camera} (body, color, film) is
 * how it is drawn in the app's camera picker and the filter it loads. {@code bulge} (0..1) bends
 * the photo like the image in a convex mirror. A text slot may carry
 * {@code background}, the colour of a pill drawn behind the line.
 *
 * <p>GROUP: a film shelf has no definition; the object must be empty.
 */
public final class CameraLookDefinitionValidator {

    private static final Set<String> FILTER_FIELDS =
            Set.of("adjust", "matrix", "lut", "strength", "effects", "group", "icon");
    private static final Set<String> FRAME_FIELDS = Set.of("images", "photoRect", "texts", "canvas", "camera", "bulge");
    private static final Set<String> CAMERA_FIELDS = Set.of("body", "color", "film");
    private static final Set<String> CAMERA_BODIES = Set.of("compact", "slr", "mini", "box", "toy");
    /** A group id, an icon name or a filter id: the app looks each up and ignores one it does not know. */
    private static final Pattern NAME = Pattern.compile("^[a-z0-9]+(_[a-z0-9]+)*$");
    private static final int MAX_NAME_LENGTH = 40;
    private static final Set<String> SHAPES = Set.of("threeTwo", "square");

    /** Field to {min, max}, both inclusive. */
    private static final Map<String, double[]> ADJUST_RANGES = Map.of(
            "brightness", new double[] {-1, 1},
            "contrast", new double[] {0, 3},
            "saturation", new double[] {0, 3},
            "warmth", new double[] {-1, 1},
            "tint", new double[] {-1, 1},
            "fade", new double[] {0, 1});
    private static final Map<String, double[]> EFFECT_RANGES = Map.of(
            "grain", new double[] {0, 1},
            "vignette", new double[] {0, 1});

    private static final int MATRIX_LENGTH = 20;
    private static final int MAX_TEXT_SLOTS = 6;
    private static final int MAX_DATE_FORMAT_LENGTH = 20;

    private static final Set<String> TEXT_FIELDS = Set.of(
            "source", "x", "y", "size", "color", "weight", "align", "uppercase", "maxWidth", "dateFormat",
            "rotation", "background");
    private static final Set<String> TEXT_SOURCES = Set.of("placeName", "province", "date", "time", "caption");
    private static final Set<String> TEXT_ALIGNS = Set.of("left", "center", "right");
    private static final Set<Integer> TEXT_WEIGHTS = Set.of(400, 500, 600, 700, 800);
    private static final Pattern COLOR = Pattern.compile("^#([0-9a-fA-F]{6}|[0-9a-fA-F]{8})$");

    /** Slack for a rectangle typed as fractions that sum to 1 only up to rounding. */
    private static final double RECT_EPSILON = 1e-6;

    private CameraLookDefinitionValidator() {
    }

    /**
     * @param isLookAssetUrl true for a URL of a file this product stores pixel-exact (the
     *                       {@code camera-looks/} prefix); every URL in a definition must pass it
     * @throws BusinessException {@link ErrorConstant#INVALID_PARAMETERS} naming the first fault
     */
    public static void validate(CameraLookKind kind, JsonNode definition, Predicate<String> isLookAssetUrl) {
        if (definition == null || !definition.isObject()) {
            throw invalid("definition must be a JSON object");
        }
        switch (kind) {
            case FILTER -> validateFilter(definition, isLookAssetUrl);
            case FRAME -> validateFrame(definition, isLookAssetUrl);
            case GROUP -> {
                if (!definition.isEmpty()) {
                    throw invalid("A film shelf has no definition; send {}");
                }
            }
        }
    }

    private static void validateFilter(JsonNode definition, Predicate<String> isLookAssetUrl) {
        requireKnownFields(definition, FILTER_FIELDS, "definition");
        JsonNode adjust = definition.get("adjust");
        JsonNode matrix = definition.get("matrix");
        if (definition.has("lut") && adjust == null && matrix == null) {
            throw invalid("A filter with a lut needs adjust or matrix (the fallback where the LUT shader cannot run)");
        }
        if (adjust != null) {
            validateRanges(adjust, ADJUST_RANGES, "adjust");
        }
        if (matrix != null) {
            if (!matrix.isArray() || matrix.size() != MATRIX_LENGTH) {
                throw invalid("matrix must be an array of " + MATRIX_LENGTH + " numbers");
            }
            for (JsonNode value : matrix) {
                requireFinite(value, "matrix");
            }
        }
        if (definition.has("lut")) {
            requireAssetUrl(definition.get("lut"), "lut", isLookAssetUrl);
        }
        if (definition.has("strength")) {
            requireInRange(definition.get("strength"), 0, 1, "strength");
        }
        if (definition.has("effects")) {
            validateRanges(definition.get("effects"), EFFECT_RANGES, "effects");
        }
        if (definition.has("group")) {
            requireName(definition.get("group"), "group");
        }
        if (definition.has("icon")) {
            requireName(definition.get("icon"), "icon");
        }
    }

    private static void validateFrame(JsonNode definition, Predicate<String> isLookAssetUrl) {
        requireKnownFields(definition, FRAME_FIELDS, "definition");
        // A text-only frame, or the plain one, has no images.
        JsonNode images = definition.has("images")
                ? definition.get("images")
                : JsonNodeFactory.instance.objectNode();
        if (!images.isObject()) {
            throw invalid("images must be an object keyed by threeTwo / square");
        }
        requireKnownFields(images, SHAPES, "images");
        for (Map.Entry<String, JsonNode> image : (Iterable<Map.Entry<String, JsonNode>>) images::fields) {
            requireAssetUrl(image.getValue(), "images." + image.getKey(), isLookAssetUrl);
        }

        JsonNode canvas = definition.get("canvas");
        if (canvas != null) {
            // The frame's own shape, as the pixel size of its images: [width, height].
            if (!canvas.isArray() || canvas.size() != 2) {
                throw invalid("canvas must be [width, height]");
            }
            for (JsonNode side : canvas) {
                requireInRange(side, 1, 8192, "canvas");
            }
        }
        if (definition.has("bulge")) {
            requireInRange(definition.get("bulge"), 0, 1, "bulge");
        }
        JsonNode camera = definition.get("camera");
        if (camera != null) {
            if (!camera.isObject()) {
                throw invalid("camera must be an object");
            }
            requireKnownFields(camera, CAMERA_FIELDS, "camera");
            if (camera.has("body")) {
                requireOneOf(camera.get("body"), CAMERA_BODIES, "camera.body");
            }
            if (camera.has("color")) {
                requireColor(camera.get("color"), "camera.color");
            }
            if (camera.has("film")) {
                requireName(camera.get("film"), "camera.film");
            }
        }

        JsonNode photoRect = definition.get("photoRect");
        if (photoRect != null) {
            requireShapesOf(photoRect, images, "photoRect");
            for (Map.Entry<String, JsonNode> rect : (Iterable<Map.Entry<String, JsonNode>>) photoRect::fields) {
                validateRect(rect.getValue(), "photoRect." + rect.getKey());
            }
        }

        JsonNode texts = definition.get("texts");
        if (texts != null) {
            // Text needs no image under it, so any shape may carry text.
            if (!texts.isObject()) {
                throw invalid("texts must be an object keyed by threeTwo / square");
            }
            requireKnownFields(texts, SHAPES, "texts");
            for (Map.Entry<String, JsonNode> shape : (Iterable<Map.Entry<String, JsonNode>>) texts::fields) {
                String path = "texts." + shape.getKey();
                JsonNode slots = shape.getValue();
                if (!slots.isArray() || slots.size() > MAX_TEXT_SLOTS) {
                    throw invalid(path + " must be an array of at most " + MAX_TEXT_SLOTS + " text slots");
                }
                int index = 0;
                for (JsonNode slot : slots) {
                    validateTextSlot(slot, path + "[" + index++ + "]");
                }
            }
        }
    }

    /** x, y, width, height as fractions of the frame image, inside it. */
    private static void validateRect(JsonNode rect, String path) {
        if (!rect.isArray() || rect.size() != 4) {
            throw invalid(path + " must be [x, y, width, height]");
        }
        for (JsonNode value : rect) {
            requireInRange(value, 0, 1, path);
        }
        double x = rect.get(0).asDouble();
        double y = rect.get(1).asDouble();
        double width = rect.get(2).asDouble();
        double height = rect.get(3).asDouble();
        if (width <= 0 || height <= 0 || x + width > 1 + RECT_EPSILON || y + height > 1 + RECT_EPSILON) {
            throw invalid(path + " must have a positive size and stay inside the frame");
        }
    }

    private static void validateTextSlot(JsonNode slot, String path) {
        if (!slot.isObject()) {
            throw invalid(path + " must be an object");
        }
        requireKnownFields(slot, TEXT_FIELDS, path);
        requireOneOf(slot.get("source"), TEXT_SOURCES, path + ".source");
        requireInRange(slot.get("x"), 0, 1, path + ".x");
        requireInRange(slot.get("y"), 0, 1, path + ".y");
        requireInRange(slot.get("size"), 0.01, 0.2, path + ".size");
        requireColor(slot.get("color"), path + ".color");
        if (slot.has("background")) {
            requireColor(slot.get("background"), path + ".background");
        }
        JsonNode weight = slot.get("weight");
        if (weight == null || !weight.isIntegralNumber() || !TEXT_WEIGHTS.contains(weight.asInt())) {
            throw invalid(path + ".weight must be 400, 500, 600, 700 or 800");
        }
        requireOneOf(slot.get("align"), TEXT_ALIGNS, path + ".align");
        JsonNode uppercase = slot.get("uppercase");
        if (uppercase == null || !uppercase.isBoolean()) {
            throw invalid(path + ".uppercase must be true or false");
        }
        requireInRange(slot.get("maxWidth"), 0.1, 1, path + ".maxWidth");
        JsonNode dateFormat = slot.get("dateFormat");
        if (dateFormat != null
                && (!dateFormat.isTextual() || dateFormat.asText().length() > MAX_DATE_FORMAT_LENGTH)) {
            throw invalid(path + ".dateFormat must be text of at most " + MAX_DATE_FORMAT_LENGTH + " characters");
        }
        if (slot.has("rotation")) {
            requireInRange(slot.get("rotation"), -45, 45, path + ".rotation");
        }
    }

    private static void validateRanges(JsonNode object, Map<String, double[]> ranges, String path) {
        if (!object.isObject()) {
            throw invalid(path + " must be an object");
        }
        requireKnownFields(object, ranges.keySet(), path);
        for (Map.Entry<String, JsonNode> field : (Iterable<Map.Entry<String, JsonNode>>) object::fields) {
            double[] range = ranges.get(field.getKey());
            requireInRange(field.getValue(), range[0], range[1], path + "." + field.getKey());
        }
    }

    /** Keys must be shapes, and only shapes the frame has an image for. */
    private static void requireShapesOf(JsonNode object, JsonNode images, String path) {
        if (!object.isObject()) {
            throw invalid(path + " must be an object keyed by threeTwo / square");
        }
        requireKnownFields(object, SHAPES, path);
        for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
            String shape = names.next();
            if (!images.has(shape)) {
                throw invalid(path + "." + shape + " has no image of that shape");
            }
        }
    }

    private static void requireColor(JsonNode color, String path) {
        if (color == null || !color.isTextual() || !COLOR.matcher(color.asText()).matches()) {
            throw invalid(path + " must be #RRGGBB or #AARRGGBB");
        }
    }

    private static void requireName(JsonNode value, String path) {
        if (value == null || !value.isTextual() || value.asText().length() > MAX_NAME_LENGTH
                || !NAME.matcher(value.asText()).matches()) {
            throw invalid(path + " must be lowercase letters, digits and underscores, at most "
                    + MAX_NAME_LENGTH + " characters");
        }
    }

    private static void requireKnownFields(JsonNode object, Set<String> allowed, String path) {
        for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw invalid("Unknown field " + path + "." + name);
            }
        }
    }

    private static void requireAssetUrl(JsonNode value, String path, Predicate<String> isLookAssetUrl) {
        if (value == null || !value.isTextual() || !isLookAssetUrl.test(value.asText())) {
            throw invalid(path + " must be the URL of a file uploaded through the camera looks upload");
        }
    }

    private static void requireOneOf(JsonNode value, Set<String> allowed, String path) {
        if (value == null || !value.isTextual() || !allowed.contains(value.asText())) {
            throw invalid(path + " must be one of " + String.join(", ", allowed.stream().sorted().toList()));
        }
    }

    private static void requireInRange(JsonNode value, double min, double max, String path) {
        requireFinite(value, path);
        double number = value.asDouble();
        if (number < min || number > max) {
            throw invalid(path + " must be between " + trim(min) + " and " + trim(max));
        }
    }

    private static void requireFinite(JsonNode value, String path) {
        if (value == null || !value.isNumber() || !Double.isFinite(value.asDouble())) {
            throw invalid(path + " must be a number");
        }
    }

    /** 1.0 reads as "1" in a message, 0.01 stays "0.01". */
    private static String trim(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorConstant.INVALID_PARAMETERS, message);
    }
}
