package com.ds.goroute.config.filter;

import com.ds.goroute.constant.RequestKeyConstant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.ServletRequest;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorsFilterTest {

    @Test
    void jsonArrayBodyReachesTheControllerUnchanged() throws Exception {
        String forwarded = forwardedBody("[\"SUPPORT\",\"MODERATOR\"]");

        assertThat(forwarded).isEqualTo("[\"SUPPORT\",\"MODERATOR\"]");
    }

    @Test
    void jsonObjectBodyStillCarriesRequestMetadata() throws Exception {
        String forwarded = forwardedBody("{\"name\":\"Hanoi\"}");

        assertThat(forwarded)
                .contains("\"name\":\"Hanoi\"")
                .contains("\"" + RequestKeyConstant.REQUEST_ID + "\"");
    }

    private String forwardedBody(String body) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/v1/api/admin/users/1/roles");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        AtomicReference<ServletRequest> forwarded = new AtomicReference<>();

        new CorsFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> forwarded.set(req));

        return new String(forwarded.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
