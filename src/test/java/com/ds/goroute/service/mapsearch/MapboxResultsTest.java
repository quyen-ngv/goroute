package com.ds.goroute.service.mapsearch;

import com.ds.goroute.entity.MapPlaceCacheEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MapboxResults and GoongShape")
class MapboxResultsTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Shaped like the Search Box answers recorded at Hồ Gươm on 2026-10-11. */
    private static final String HO_GUOM = """
            {"type":"FeatureCollection","features":[
              {"type":"Feature","geometry":{"type":"Point","coordinates":[105.8526,21.0288]},
               "properties":{"name":"Tháp Rùa","mapbox_id":"dXJuOm1ieHBvaTox","feature_type":"poi",
                 "full_address":"Hà Nội, 11000, Việt Nam","place_formatted":"Hà Nội, 11000, Việt Nam",
                 "coordinates":{"latitude":21.0288,"longitude":105.8526},
                 "poi_category":["hồ","ngoài trời"],"metadata":{"phone":"+84 24 0000"},
                 "context":{"country":{"country_code":"vn"}}}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[105.8527,21.0289]},
               "properties":{"name":"Tháp Rùa","mapbox_id":"dXJuOm1ieHBvaTox2","feature_type":"poi",
                 "place_formatted":"P. Lê Thái Tổ, Hà Nội","coordinates":{"latitude":21.0289,"longitude":105.8527}}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[105.8524,21.0287]},
               "properties":{"name":"還劍湖","mapbox_id":"dXJuOm1ieHBvaToz","feature_type":"poi",
                 "coordinates":{"latitude":21.0287,"longitude":105.8524}}},
              {"type":"Feature","geometry":{"type":"Point","coordinates":[105.85,21.03]},
               "properties":{"feature_type":"poi"}}
            ]}""";

    @Test
    @DisplayName("reads id, name, position, address, categories and country; skips features without an id")
    void readsFeatures() throws Exception {
        List<MapPlaceCacheEntry> places = MapboxResults.places(objectMapper.readTree(HO_GUOM));

        assertThat(places).hasSize(3);
        MapPlaceCacheEntry first = places.get(0);
        assertThat(first.getProviderPlaceId()).isEqualTo("dXJuOm1ieHBvaTox");
        assertThat(first.getName()).isEqualTo("Tháp Rùa");
        assertThat(first.getNameNormalized()).isEqualTo("thap rua");
        assertThat(first.getLatitude()).isEqualTo(21.0288);
        assertThat(first.getCategories()).isEqualTo("hồ, ngoài trời");
        assertThat(first.getPhone()).isEqualTo("+84 24 0000");
        assertThat(first.getCountryCode()).isEqualTo("VN");
        assertThat(places.get(1).getAddress()).isEqualTo("Tháp Rùa, P. Lê Thái Tổ, Hà Nội");
    }

    @Test
    @DisplayName("drops Chinese names from a Vietnamese answer and the same place listed twice nearby")
    void cleans() throws Exception {
        List<MapPlaceCacheEntry> places = MapboxResults.places(objectMapper.readTree(HO_GUOM));

        assertThat(MapboxResults.clean(places, "vi"))
                .extracting(MapPlaceCacheEntry::getProviderPlaceId)
                .containsExactly("dXJuOm1ieHBvaTox");
        assertThat(MapboxResults.clean(places, "zh"))
                .extracting(MapPlaceCacheEntry::getProviderPlaceId)
                .containsExactly("dXJuOm1ieHBvaTox", "dXJuOm1ieHBvaToz");
    }

    @Test
    @DisplayName("autocomplete answers in Goong's shape with a mapbox: id the app can send back")
    void autocompleteShape() throws Exception {
        MapPlaceCacheEntry place = MapboxResults.places(objectMapper.readTree(HO_GUOM)).get(1);

        JsonNode prediction = new GoongShape(objectMapper).autocomplete(List.of(place)).path("predictions").get(0);

        assertThat(prediction.path("place_id").asText()).isEqualTo("mapbox:dXJuOm1ieHBvaTox2");
        assertThat(prediction.path("structured_formatting").path("main_text").asText()).isEqualTo("Tháp Rùa");
        assertThat(prediction.path("structured_formatting").path("secondary_text").asText())
                .isEqualTo("P. Lê Thái Tổ, Hà Nội");
        assertThat(prediction.path("description").asText()).isEqualTo("Tháp Rùa, P. Lê Thái Tổ, Hà Nội");
        assertThat(prediction.path("geometry").path("location").path("lat").asDouble()).isEqualTo(21.0289);
    }

    @Test
    @DisplayName("detail and geocode answers carry Goong's result fields and the compound")
    void detailShape() throws Exception {
        MapPlaceCacheEntry place = MapboxResults.places(objectMapper.readTree(HO_GUOM)).get(0);
        GoongShape shape = new GoongShape(objectMapper);
        GoongShape.Compound compound = new GoongShape.Compound("Phường Hoàn Kiếm", null, "Hà Nội");

        JsonNode result = shape.detail(place, compound).path("result");
        assertThat(result.path("formatted_address").asText()).isEqualTo("Hà Nội, 11000, Việt Nam");
        assertThat(result.path("types")).hasSize(2);
        assertThat(result.path("compound").path("commune").asText()).isEqualTo("Phường Hoàn Kiếm");
        assertThat(result.path("compound").has("district")).isFalse();

        JsonNode results = shape.geocode(List.of(place), compound).path("results");
        assertThat(results.get(0).path("name").asText()).isEqualTo("Tháp Rùa");

        ObjectNode saving = GoongShape.savingMode(shape.geocode(List.of(), null), null);
        assertThat(saving.path("saving_mode").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("one normal form for names and queries")
    void normalizes() {
        assertThat(MapText.normalize("Chợ Bến Thành")).isEqualTo("cho ben thanh");
        assertThat(MapText.normalize("  ĐÀ   Lạt! ")).isEqualTo("da lat");
        assertThat(MapText.normalize(null)).isEmpty();
        assertThat(MapText.key("a", 1)).hasSize(64).isEqualTo(MapText.key("a", 1));
    }
}
