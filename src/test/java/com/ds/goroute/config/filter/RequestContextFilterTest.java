package com.ds.goroute.config.filter;

import com.ds.goroute.constant.RequestKeyConstant;
import jakarta.servlet.ServletRequest;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RequestContextFilterTest {

    @Test
    void jsonArrayBodyReachesTheControllerUnchanged() throws Exception {
        assertThat(forwardedBody("[\"SUPPORT\",\"MODERATOR\"]", null))
                .isEqualTo("[\"SUPPORT\",\"MODERATOR\"]");
    }

    @Test
    void jsonObjectBodyReachesTheControllerUnchanged() throws Exception {
        String body = "{\"name\":\"Hanoi\",\"big\":123456789012345678901234567890}";

        assertThat(forwardedBody(body, null)).isEqualTo(body);
    }

    @Test
    void neverCopiesTheApiSecretIntoTheBody() throws Exception {
        assertThat(forwardedBody("{\"name\":\"Hanoi\"}", "s3cret")).doesNotContain("s3cret");
    }

    @Test
    void malformedJsonIsLeftForTheControllerToReject() throws Exception {
        assertThat(forwardedBody("{\"events\":[", null)).isEqualTo("{\"events\":[");
    }

    @Test
    void usesTheCallersRequestIdAndClearsTheMdcAfterwards() throws Exception {
        MockHttpServletRequest request = jsonRequest("{}");
        request.addHeader(RequestKeyConstant.X_REQUEST_ID, "req-42");
        AtomicReference<String> attribute = new AtomicReference<>();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        new RequestContextFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            attribute.set((String) req.getAttribute(RequestKeyConstant.REQUEST_ID));
            mdcDuringRequest.set(MDC.get(RequestKeyConstant.THREAD_REQUEST_ID));
        });

        assertThat(attribute.get()).isEqualTo("req-42");
        assertThat(mdcDuringRequest.get()).isEqualTo("req-42");
        assertThat(MDC.get(RequestKeyConstant.THREAD_REQUEST_ID)).isNull();
    }

    @Test
    void generatesARequestIdWhenNoneIsSent() throws Exception {
        AtomicReference<Object> attribute = new AtomicReference<>();

        new RequestContextFilter().doFilter(jsonRequest("{}"), new MockHttpServletResponse(),
                (req, res) -> attribute.set(req.getAttribute(RequestKeyConstant.REQUEST_ID)));

        assertThat(attribute.get()).asString().isNotBlank();
    }

    @Test
    void refusesABodyDeclaredTooLarge() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/api/trips") {
            @Override
            public long getContentLengthLong() {
                return ApiKeyVerifyRequestWrapper.DEFAULT_MAX_BODY_CHARS + 1L;
            }
        };
        request.setContentType("application/json");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ServletRequest> forwarded = new AtomicReference<>();

        new RequestContextFilter().doFilter(request, response, (req, res) -> forwarded.set(req));

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(forwarded.get()).isNull();
    }

    private static String forwardedBody(String body, String apiSecret) throws Exception {
        MockHttpServletRequest request = jsonRequest(body);
        if (apiSecret != null) {
            request.addHeader(RequestKeyConstant.X_API_SECRET, apiSecret);
        }
        AtomicReference<ServletRequest> forwarded = new AtomicReference<>();

        new RequestContextFilter().doFilter(request, new MockHttpServletResponse(),
                (req, res) -> forwarded.set(req));

        return new String(forwarded.get().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private static MockHttpServletRequest jsonRequest(String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/v1/api/admin/users/1/roles");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        return request;
    }
}
