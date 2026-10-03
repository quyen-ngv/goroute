package com.ds.goroute.thirdparty.google.places;

import com.ds.goroute.config.GooglePlacesProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Response bodies follow the Places API (New) reference, since there is no key to record real ones yet. */
class GooglePlacesClientTest {

    private static final String BASE_URL = "https://places.googleapis.com";

    private GooglePlacesProperties properties;
    private MockRestServiceServer server;
    private GooglePlacesClient client;

    @BeforeEach
    void setUp() {
        properties = new GooglePlacesProperties();
        properties.setApiKey("test-key");
        properties.setBaseUrl(BASE_URL);
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GooglePlacesClient(builder, properties);
    }

    @Test
    void textSearchSendsKeyFieldMaskAndBodyAndReadsPlaces() {
        server.expect(requestTo(BASE_URL + "/v1/places:searchText"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesClient.SEARCH_FIELDS))
                .andExpect(content().json("""
                        {"textQuery":"Cong Ca Phe Ly Tu Trong","pageSize":3,
                         "locationBias":{"circle":{"center":{"latitude":10.7769,"longitude":106.7009},"radius":5000.0}}}
                        """, true))
                .andRespond(withSuccess("""
                        {"places":[{
                          "id":"ChIJ0T2NLikpdTERKxE8d61aX_E",
                          "types":["cafe","food","point_of_interest","establishment"],
                          "formattedAddress":"26 Lý Tự Trọng, Bến Nghé, Quận 1, Hồ Chí Minh, Việt Nam",
                          "location":{"latitude":10.7772,"longitude":106.7012},
                          "googleMapsUri":"https://maps.google.com/?cid=17393364373045178667",
                          "displayName":{"text":"Cộng Cà Phê","languageCode":"vi"},
                          "primaryType":"cafe"
                        }]}""", MediaType.APPLICATION_JSON));

        List<GooglePlace> places = client.searchText(GooglePlacesTextSearchRequest.builder()
                .textQuery("Cong Ca Phe Ly Tu Trong")
                .pageSize(3)
                .locationBias(new GooglePlacesTextSearchRequest.LocationBias(new GooglePlacesTextSearchRequest.Circle(
                        new GooglePlacesTextSearchRequest.Center(new BigDecimal("10.7769"), new BigDecimal("106.7009")),
                        5000.0)))
                .build());

        server.verify();
        assertThat(places).singleElement().satisfies(place -> {
            assertThat(place.getId()).isEqualTo("ChIJ0T2NLikpdTERKxE8d61aX_E");
            assertThat(place.getDisplayName().getText()).isEqualTo("Cộng Cà Phê");
            assertThat(place.getLocation().getLatitude()).isEqualByComparingTo("10.7772");
            assertThat(place.getPrimaryType()).isEqualTo("cafe");
            assertThat(place.getGoogleMapsUri()).isEqualTo("https://maps.google.com/?cid=17393364373045178667");
        });
    }

    @Test
    void textSearchWithoutMatchesReturnsAnEmptyList() {
        server.expect(requestTo(BASE_URL + "/v1/places:searchText"))
                .andExpect(content().json("""
                        {"textQuery":"zzz","pageSize":1}""", true))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(client.searchText(GooglePlacesTextSearchRequest.builder().textQuery("zzz").pageSize(1).build()))
                .isEmpty();
    }

    @Test
    void textSearchErrorBecomesAGooglePlacesExceptionWithOnlyTheStatus() {
        server.expect(requestTo(BASE_URL + "/v1/places:searchText"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":403,\"message\":\"API key not valid\"}}"));

        assertThatThrownBy(() -> client.searchText(GooglePlacesTextSearchRequest.builder().textQuery("x").build()))
                .isInstanceOf(GooglePlacesException.class)
                .hasMessage("Google Places text search answered 403");
    }

    @Test
    void placeDetailsUseThePlaceFieldMask() {
        server.expect(requestTo(BASE_URL + "/v1/places/ChIJ0T2NLikpdTERKxE8d61aX_E"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Goog-Api-Key", "test-key"))
                .andExpect(header("X-Goog-FieldMask", GooglePlacesClient.PLACE_FIELDS))
                .andRespond(withSuccess("""
                        {"id":"ChIJ0T2NLikpdTERKxE8d61aX_E","displayName":{"text":"Cộng Cà Phê"},
                         "location":{"latitude":10.7772,"longitude":106.7012}}""", MediaType.APPLICATION_JSON));

        Optional<GooglePlace> place = client.getPlace("ChIJ0T2NLikpdTERKxE8d61aX_E");

        assertThat(place).get().extracting(GooglePlace::getId).isEqualTo("ChIJ0T2NLikpdTERKxE8d61aX_E");
    }

    @Test
    void placeDetailsForAnUnknownIdAreEmpty() {
        server.expect(requestTo(BASE_URL + "/v1/places/ChIJgone"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON).body("{}"));

        assertThat(client.getPlace("ChIJgone")).isEmpty();
    }

    @Test
    void withoutAKeyNothingIsSentToGoogle() {
        properties.setApiKey(" ");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.searchText(GooglePlacesTextSearchRequest.builder().textQuery("x").build()))
                .isInstanceOf(GooglePlacesException.class)
                .hasMessage("Google Places API key is not configured");
        server.verify();
    }
}
