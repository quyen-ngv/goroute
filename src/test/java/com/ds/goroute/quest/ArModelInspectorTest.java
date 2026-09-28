package com.ds.goroute.quest;

import com.ds.goroute.quest.service.ArModelInspector;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AR model inspector")
class ArModelInspectorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    /** A GLB with only a JSON chunk: enough for everything the inspector reads. */
    private static byte[] glb(String json) {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        int padded = (body.length + 3) / 4 * 4;
        ByteBuffer buffer = ByteBuffer.allocate(20 + padded).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x46546C67).putInt(2).putInt(20 + padded).putInt(padded).putInt(0x4E4F534A);
        buffer.put(body);
        while (buffer.hasRemaining()) {
            buffer.put((byte) ' ');
        }
        return buffer.array();
    }

    @Test
    @DisplayName("reads triangles, bones and clips with their lengths; a walk clip lets it wander")
    void readsAGoodModel() {
        String json = """
                {"asset":{"version":"2.0"},
                 "accessors":[{"count":3000},{"count":600},{"count":10,"max":[2.5]},{"count":10,"max":[1.25]}],
                 "meshes":[{"primitives":[{"indices":0,"attributes":{"POSITION":1}}]}],
                 "skins":[{"joints":[0,1,2,3]}],
                 "animations":[
                   {"name":"idle","samplers":[{"input":2}]},
                   {"name":"walk","samplers":[{"input":3},{"input":2}]},
                   {"name":"dance","samplers":[{"input":3}]}]}
                """;
        ArModelInspector.Inspection inspection = ArModelInspector.inspectGlb(glb(json), mapper);

        assertThat(inspection.usable()).isTrue();
        assertThat(inspection.triangles()).isEqualTo(1000);
        assertThat(inspection.joints()).isEqualTo(4);
        assertThat(inspection.clips()).extracting(ArModelInspector.Clip::name).containsExactly("idle", "walk", "dance");
        assertThat(inspection.clips().get(1).seconds()).isEqualTo(2.5);
        assertThat(inspection.canWander()).isTrue();
        assertThat(inspection.warnings()).anyMatch(w -> w.contains("dance"));
    }

    @Test
    @DisplayName("refuses what the Android loader cannot read: outside files, WebP textures, too many triangles and bones")
    void refusesWhatPhonesCannotShow() {
        String json = """
                {"extensionsRequired":["EXT_texture_webp"],
                 "buffers":[{"uri":"model.bin","byteLength":10}],
                 "images":[{"uri":"texture.png"}],
                 "accessors":[{"count":3300000}],
                 "meshes":[{"primitives":[{"indices":0}]}],
                 "skins":[{"joints":[%s]}]}
                """.formatted("0,".repeat(300) + "0");
        ArModelInspector.Inspection inspection = ArModelInspector.inspectGlb(glb(json), mapper);

        assertThat(inspection.usable()).isFalse();
        assertThat(String.join("|", inspection.errors()))
                .contains("EXT_texture_webp")
                .contains("outside buffer")
                .contains("outside texture")
                .contains("1100000 triangles; at most 1000000")
                .contains("301 bones");
        assertThat(inspection.canWander()).isFalse();
    }

    @Test
    @DisplayName("compressed meshes and KTX2 textures are allowed (Filament reads them); heavy ones only warn")
    void compressionAllowed() {
        String json = """
                {"extensionsUsed":["KHR_draco_mesh_compression","KHR_texture_basisu"],
                 "extensionsRequired":["KHR_draco_mesh_compression","KHR_texture_basisu"],
                 "accessors":[{"count":900000}],
                 "meshes":[{"primitives":[{"indices":0}]}]}
                """;
        ArModelInspector.Inspection inspection = ArModelInspector.inspectGlb(glb(json), mapper);

        assertThat(inspection.usable()).isTrue();
        assertThat(inspection.triangles()).isEqualTo(300000);
        assertThat(String.join("|", inspection.warnings()))
                .contains("300000 triangles")
                .contains("Compressed mesh");
    }

    @Test
    @DisplayName("the triangle ceiling is the one passed in (the config)")
    void ceilingFromConfig() {
        String json = """
                {"accessors":[{"count":300}],"meshes":[{"primitives":[{"indices":0}]}]}
                """;
        assertThat(ArModelInspector.inspectGlb(glb(json), mapper, 50).errors())
                .anyMatch(e -> e.contains("100 triangles; at most 50"));
    }

    @Test
    @DisplayName("a file that is not a GLB is refused, not crashed on")
    void notAGlb() {
        assertThat(ArModelInspector.inspectGlb("hello world, not a model".getBytes(), mapper).usable()).isFalse();
        assertThat(ArModelInspector.inspectGlb(new byte[3], mapper).usable()).isFalse();
    }

    @Test
    @DisplayName("a USDZ is a zip whose first entry is a USD layer")
    void usdz() throws Exception {
        assertThat(ArModelInspector.isUsdz(zip("scroll.usdc"))).isTrue();
        assertThat(ArModelInspector.isUsdz(zip("readme.txt"))).isFalse();
        assertThat(ArModelInspector.isUsdz("PK but not really".getBytes())).isFalse();
    }

    private static byte[] zip(String firstEntry) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(firstEntry));
            zip.write(new byte[64]);
            zip.closeEntry();
        }
        return out.toByteArray();
    }
}
