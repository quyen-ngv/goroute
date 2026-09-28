package com.ds.goroute.quest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads an uploaded 3D model far enough to check it against the AR asset spec (§3.15,
 * scripts/ar/README.md) without a 3D library: the GLB's JSON chunk says everything that
 * matters — compression extensions, external files, triangle count, skin size and the animation
 * clips with their lengths (a clip's length is the largest {@code max} of its samplers' input
 * accessors, which glTF requires to be present).
 *
 * <p>A USDZ is only checked to be what it claims: a zip whose first entry is a USD layer.
 */
public final class ArModelInspector {

    /** The default ceiling; the live one is the QUEST.AR_MAX_TRIANGLES config. */
    public static final int MAX_TRIANGLES = 1_000_000;
    /** Above this a mid-range phone may drop frames, more so with a skinned (animated) model. */
    public static final int WARN_TRIANGLES = 150_000;
    /** Filament's per-skin limit (CONFIG_MAX_BONE_COUNT); more makes the model fail to load. */
    public static final int MAX_JOINTS = 256;
    public static final long WARN_BYTES = 5L * 1024 * 1024;

    private static final int GLB_MAGIC = 0x46546C67; // "glTF"
    private static final int CHUNK_JSON = 0x4E4F534A; // "JSON"
    /**
     * Texture formats the Android loader cannot decode: Filament's gltfio registers PNG, JPEG and
     * KTX2 only. Mesh compression (Draco, meshopt), quantized attributes and KTX2/Basis textures are
     * all read by Filament 1.56 and allowed (the GLB is only ever shown on Android; iOS gets the
     * USDZ).
     */
    private static final Set<String> UNSUPPORTED_EXTENSIONS = Set.of("EXT_texture_webp", "EXT_texture_avif");
    private static final Set<String> UNSUPPORTED_IMAGE_TYPES = Set.of("image/webp", "image/avif");

    private ArModelInspector() {
    }

    /** One animation clip: its name as authored and its length in seconds. */
    public record Clip(String name, double seconds) {
    }

    /**
     * @param errors   why the file cannot be used; empty when it can.
     * @param warnings what is allowed but outside the recommended budget.
     */
    public record Inspection(int triangles, int joints, List<Clip> clips, boolean canWander,
                             List<String> errors, List<String> warnings) {

        public boolean usable() {
            return errors.isEmpty();
        }
    }

    public static Inspection inspectGlb(byte[] bytes, ObjectMapper mapper) {
        return inspectGlb(bytes, mapper, MAX_TRIANGLES);
    }

    /** @param maxTriangles the ceiling in force (the QUEST.AR_MAX_TRIANGLES config). */
    public static Inspection inspectGlb(byte[] bytes, ObjectMapper mapper, int maxTriangles) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (bytes == null || bytes.length < 20) {
            errors.add("The file is not a GLB model");
            return new Inspection(0, 0, List.of(), false, errors, warnings);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (buffer.getInt(0) != GLB_MAGIC) {
            errors.add("The file is not a GLB model (binary glTF)");
            return new Inspection(0, 0, List.of(), false, errors, warnings);
        }
        if (buffer.getInt(4) != 2) {
            errors.add("Only glTF 2.0 is supported");
            return new Inspection(0, 0, List.of(), false, errors, warnings);
        }
        int jsonLength = buffer.getInt(12);
        if (buffer.getInt(16) != CHUNK_JSON || jsonLength <= 0 || 20L + jsonLength > bytes.length) {
            errors.add("The GLB has no readable JSON chunk");
            return new Inspection(0, 0, List.of(), false, errors, warnings);
        }
        JsonNode gltf;
        try {
            gltf = mapper.readTree(new String(bytes, 20, jsonLength, StandardCharsets.UTF_8));
        } catch (Exception ex) {
            errors.add("The GLB's JSON chunk cannot be read");
            return new Inspection(0, 0, List.of(), false, errors, warnings);
        }

        for (JsonNode ext : gltf.path("extensionsRequired")) {
            if (UNSUPPORTED_EXTENSIONS.contains(ext.asText())) {
                errors.add("Uses " + ext.asText() + "; export textures as PNG, JPEG or KTX2");
            }
        }
        for (JsonNode image : gltf.path("images")) {
            String uri = image.path("uri").asText("");
            String type = image.path("mimeType").asText("");
            if (UNSUPPORTED_IMAGE_TYPES.contains(type)
                    || UNSUPPORTED_IMAGE_TYPES.stream().anyMatch(t -> uri.startsWith("data:" + t))) {
                errors.add("Has a WebP/AVIF texture; export textures as PNG, JPEG or KTX2");
                break;
            }
        }
        java.util.Set<String> used = new java.util.HashSet<>();
        gltf.path("extensionsUsed").forEach(ext -> used.add(ext.asText()));
        if (used.contains("KHR_draco_mesh_compression") || used.contains("EXT_meshopt_compression")) {
            warnings.add("Compressed mesh: a smaller download, but phones take a little longer to open it");
        }
        for (JsonNode node : gltf.path("buffers")) {
            if (isExternal(node.path("uri"))) {
                errors.add("Refers to an outside buffer file; export as a single .glb");
                break;
            }
        }
        for (JsonNode node : gltf.path("images")) {
            if (isExternal(node.path("uri"))) {
                errors.add("Refers to an outside texture file; embed textures in the .glb");
                break;
            }
        }

        JsonNode accessors = gltf.path("accessors");
        long triangles = 0;
        for (JsonNode mesh : gltf.path("meshes")) {
            for (JsonNode primitive : mesh.path("primitives")) {
                int mode = primitive.path("mode").asInt(4);
                JsonNode counted = primitive.has("indices")
                        ? accessors.path(primitive.path("indices").asInt())
                        : accessors.path(primitive.path("attributes").path("POSITION").asInt(-1));
                long count = counted.path("count").asLong(0);
                triangles += switch (mode) {
                    case 4 -> count / 3;           // TRIANGLES
                    case 5, 6 -> Math.max(0, count - 2); // STRIP, FAN
                    default -> 0;                   // points and lines draw no faces
                };
            }
        }
        if (triangles > maxTriangles) {
            errors.add(triangles + " triangles; at most " + maxTriangles);
        } else if (triangles > WARN_TRIANGLES) {
            warnings.add(triangles + " triangles; above " + WARN_TRIANGLES
                    + " mid-range phones may stutter, more so when the model is animated");
        }

        int joints = 0;
        for (JsonNode skin : gltf.path("skins")) {
            joints = Math.max(joints, skin.path("joints").size());
        }
        if (joints > MAX_JOINTS) {
            errors.add(joints + " bones in one skin; at most " + MAX_JOINTS);
        }

        List<Clip> clips = new ArrayList<>();
        JsonNode animations = gltf.path("animations");
        for (int i = 0; i < animations.size(); i++) {
            JsonNode animation = animations.get(i);
            double seconds = 0;
            for (JsonNode sampler : animation.path("samplers")) {
                JsonNode max = accessors.path(sampler.path("input").asInt(-1)).path("max");
                if (max.isArray() && !max.isEmpty()) {
                    seconds = Math.max(seconds, max.get(0).asDouble(0));
                }
            }
            String name = animation.path("name").asText("").trim();
            clips.add(new Clip(name.isEmpty() ? "clip" + (i + 1) : name, Math.round(seconds * 1000) / 1000.0));
        }
        boolean canWander = clips.stream().anyMatch(c -> "walk".equals(c.name().toLowerCase(Locale.ROOT)));
        for (Clip clip : clips) {
            String name = clip.name().toLowerCase(Locale.ROOT);
            if (!Set.of("idle", "walk", "collect").contains(name)) {
                warnings.add("Clip \"" + clip.name() + "\" is not idle, walk or collect and will not be played");
            }
        }
        if (bytes.length > WARN_BYTES) {
            warnings.add("Larger than 5 MB; players download it with the quest");
        }
        return new Inspection((int) Math.min(triangles, Integer.MAX_VALUE), joints, List.copyOf(clips), canWander,
                List.copyOf(errors), List.copyOf(warnings));
    }

    /** True for a zip whose first entry is a USD layer (.usdc, .usda or .usd), as a USDZ must be. */
    public static boolean isUsdz(byte[] bytes) {
        if (bytes == null || bytes.length < 30 || bytes[0] != 'P' || bytes[1] != 'K' || bytes[2] != 3 || bytes[3] != 4) {
            return false;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int nameLength = Short.toUnsignedInt(buffer.getShort(26));
        if (30 + nameLength > bytes.length) {
            return false;
        }
        String first = new String(bytes, 30, nameLength, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return first.endsWith(".usdc") || first.endsWith(".usda") || first.endsWith(".usd");
    }

    private static boolean isExternal(JsonNode uri) {
        return uri.isTextual() && !uri.asText().startsWith("data:");
    }
}
