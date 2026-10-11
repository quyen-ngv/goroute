package com.ds.goroute.thirdparty.mapbox;

import com.ds.goroute.config.MapboxProperties;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

@DisplayName("MapboxClient")
class MapboxClientTest {

    private MockRestServiceServer server;
    private MapboxClient client;
    private final MapboxProperties properties = new MapboxProperties();

    @BeforeEach
    void setUp() {
        properties.setServerAccessToken("pk.test");
        properties.setBaseUrl("https://api.mapbox.test");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new MapboxClient(builder.build(), properties);
    }

    @Test
    @DisplayName("forward search sends the query, position, language, auto_complete and the token")
    void forward() {
        server.expect(requestTo(startsWith("https://api.mapbox.test/search/searchbox/v1/forward")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "Ch%E1%BB%A3%20B%E1%BA%BFn%20Th%C3%A0nh%20%26%20c%C3%A0%20ph%C3%AA"))
                .andExpect(queryParam("proximity", "106.698000,10.772500"))
                .andExpect(queryParam("language", "vi"))
                .andExpect(queryParam("limit", "5"))
                .andExpect(queryParam("auto_complete", "true"))
                .andExpect(queryParam("access_token", "pk.test"))
                .andRespond(withSuccess("{\"features\":[]}", MediaType.APPLICATION_JSON));

        JsonNode body = client.forward("Chợ Bến Thành & cà phê", 10.7725, 106.698, "vi", 5);

        assertThat(body.path("features").isArray()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("directions puts every point in the path as lng,lat and asks for per-step geometry")
    void directions() {
        server.expect(requestTo(startsWith(
                        "https://api.mapbox.test/directions/v5/mapbox/driving/105.852400,21.028700;105.834900,21.036800")))
                .andExpect(queryParam("geometries", "polyline6"))
                .andExpect(queryParam("steps", "true"))
                .andRespond(withSuccess("{\"routes\":[]}", MediaType.APPLICATION_JSON));

        client.directions("driving", List.of(new double[]{105.8524, 21.0287}, new double[]{105.8349, 21.0368}));

        server.verify();
    }

    @Test
    @DisplayName("an error status becomes a MapboxException carrying it")
    void errorStatus() {
        server.expect(requestTo(startsWith("https://api.mapbox.test/search/searchbox/v1/reverse")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.reverse(21.0, 105.0, "vi", 5))
                .isInstanceOf(MapboxException.class)
                .extracting(exception -> ((MapboxException) exception).status())
                .isEqualTo(429);
    }

    @Test
    @DisplayName("without a token nothing is sent")
    void unconfigured() {
        properties.setServerAccessToken("");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.geocodeForward("Đà Lạt", "vi", 1)).isInstanceOf(MapboxException.class);
        server.verify();
    }
}
