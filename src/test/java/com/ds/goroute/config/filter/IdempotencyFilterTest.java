package com.ds.goroute.config.filter;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.service.idempotency.IdempotencyService;
import com.ds.goroute.service.idempotency.IdempotencyService.Claim;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class IdempotencyFilterTest {

    private static final String BODY = "{\"name\":\"Hue\"}";

    private final IdempotencyService service = mock(IdempotencyService.class);
    private final HandlerMapping handlerMapping = mock(HandlerMapping.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID user = UUID.randomUUID();
    private final UUID recordId = UUID.randomUUID();
    private IdempotencyFilter filter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        ObjectProvider<HandlerMapping> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(handlerMapping);
        filter = new IdempotencyFilter(service, objectMapper, provider);
        handledBy("create");
    }

    private void handledBy(String methodName) throws Exception {
        Method method = SampleController.class.getMethod(methodName, String.class);
        when(handlerMapping.getHandler(any(HttpServletRequest.class)))
                .thenReturn(new HandlerExecutionChain(new HandlerMethod(new SampleController(), method)));
    }

    private MockHttpServletRequest request(String key, String body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/api/trips");
        request.setContentType("application/json");
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        if (key != null) {
            request.addHeader(IdempotencyFilter.HEADER, key);
        }
        request.setAttribute(RequestKeyConstant.USER_ID, user);
        return request;
    }

    /** The controller stand-in: reads the body and answers 201 with a JSON envelope. */
    private static FilterChain controller(int status, AtomicInteger calls, AtomicReference<String> bodySeen) {
        return (req, res) -> {
            calls.incrementAndGet();
            bodySeen.set(new String(req.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            HttpServletResponse response = (HttpServletResponse) res;
            response.setStatus(status);
            response.setContentType("application/json");
            response.getOutputStream().write("{\"meta\":{\"code\":200000},\"data\":1}".getBytes(StandardCharsets.UTF_8));
        };
    }

    @Test
    void requestWithoutTheHeaderPassesThroughUntouched() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request(null, BODY), response, controller(201, calls, new AtomicReference<>()));

        assertThat(calls).hasValue(1);
        assertThat(response.getStatus()).isEqualTo(201);
        verifyNoInteractions(service);
    }

    @Test
    void firstRequestRunsOnceAndItsResponseIsStored() throws Exception {
        when(service.claim(eq(user), eq("k1"), eq("POST"), eq("/v1/api/trips"), anyString()))
                .thenReturn(new Claim.Acquired(recordId));
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> seen = new AtomicReference<>();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("k1", BODY), response, controller(201, calls, seen));

        assertThat(calls).hasValue(1);
        assertThat(seen.get()).as("the controller still reads the body that was hashed").isEqualTo(BODY);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentAsString()).isEqualTo("{\"meta\":{\"code\":200000},\"data\":1}");
        verify(service).complete(eq(recordId), eq(201), eq("application/json"),
                eq("{\"meta\":{\"code\":200000},\"data\":1}".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void completedRepeatIsReplayedWithoutRunningTheController() throws Exception {
        byte[] stored = "{\"meta\":{\"code\":200000},\"data\":\"stored\"}".getBytes(StandardCharsets.UTF_8);
        when(service.claim(any(), any(), any(), any(), any()))
                .thenReturn(new Claim.Replay(201, "application/json", stored));
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("k1", BODY), response, controller(201, calls, new AtomicReference<>()));

        assertThat(calls).hasValue(0);
        assertThat(response.getStatus()).isEqualTo(201);
        assertThat(response.getContentType()).isEqualTo("application/json");
        assertThat(response.getHeader(IdempotencyFilter.REPLAYED_HEADER)).isEqualTo("true");
        assertThat(response.getContentAsByteArray()).isEqualTo(stored);
    }

    @Test
    void repeatWhileInProgressIsAConflictInTheStandardEnvelope() throws Exception {
        when(service.claim(any(), any(), any(), any(), any())).thenReturn(new Claim.InProgress());
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("k1", BODY), response, controller(201, calls, new AtomicReference<>()));

        assertThat(calls).hasValue(0);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).at("/meta/code").asInt())
                .isEqualTo(ErrorConstant.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void sameKeyOnADifferentRequestIsUnprocessable() throws Exception {
        when(service.claim(any(), any(), any(), any(), any())).thenReturn(new Claim.Mismatch());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("k1", BODY), response, controller(201, new AtomicInteger(), new AtomicReference<>()));

        assertThat(response.getStatus()).isEqualTo(422);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).at("/meta/code").asInt())
                .isEqualTo(ErrorConstant.IDEMPOTENCY_KEY_REUSED);
    }

    @Test
    void serverErrorReleasesTheKey() throws Exception {
        when(service.claim(any(), any(), any(), any(), any())).thenReturn(new Claim.Acquired(recordId));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("k1", BODY), response, controller(500, new AtomicInteger(), new AtomicReference<>()));

        assertThat(response.getStatus()).isEqualTo(500);
        verify(service).release(recordId);
        verify(service, never()).complete(any(), any(Integer.class), any(), any());
    }

    @Test
    void exceptionReleasesTheKeyAndPropagates() {
        when(service.claim(any(), any(), any(), any(), any())).thenReturn(new Claim.Acquired(recordId));
        FilterChain failing = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request("k1", BODY), new MockHttpServletResponse(), failing))
                .isInstanceOf(IllegalStateException.class);
        verify(service).release(recordId);
    }

    @Test
    void fingerprintCoversMethodPathQueryAndBody() {
        byte[] body = BODY.getBytes(StandardCharsets.UTF_8);
        String base = IdempotencyFilter.fingerprint("POST", "/v1/api/trips", body);

        assertThat(IdempotencyFilter.fingerprint("POST", "/v1/api/trips", body)).isEqualTo(base).hasSize(64);
        assertThat(IdempotencyFilter.fingerprint("PUT", "/v1/api/trips", body)).isNotEqualTo(base);
        assertThat(IdempotencyFilter.fingerprint("POST", "/v1/api/trips?x=1", body)).isNotEqualTo(base);
        assertThat(IdempotencyFilter.fingerprint("POST", "/v1/api/trips", "{}".getBytes(StandardCharsets.UTF_8)))
                .isNotEqualTo(base);
    }

    @Test
    void endpointsWithTheirOwnIdempotencyKeyHandlingAreLeftAlone() throws Exception {
        handledBy("createWithOwnKey");
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request("k1", BODY), new MockHttpServletResponse(), controller(201, calls, new AtomicReference<>()));

        assertThat(calls).hasValue(1);
        verifyNoInteractions(service);
    }

    @Test
    void streamingEndpointsAreLeftAlone() throws Exception {
        handledBy("stream");

        filter.doFilter(request("k1", BODY), new MockHttpServletResponse(),
                controller(200, new AtomicInteger(), new AtomicReference<>()));

        verifyNoInteractions(service);
    }

    @Test
    void multipartAndAnonymousRequestsAreLeftAlone() throws Exception {
        MockHttpServletRequest multipart = request("k1", BODY);
        multipart.setContentType("multipart/form-data; boundary=x");
        filter.doFilter(multipart, new MockHttpServletResponse(),
                controller(201, new AtomicInteger(), new AtomicReference<>()));

        MockHttpServletRequest anonymous = request("k1", BODY);
        anonymous.removeAttribute(RequestKeyConstant.USER_ID);
        filter.doFilter(anonymous, new MockHttpServletResponse(),
                controller(201, new AtomicInteger(), new AtomicReference<>()));

        verifyNoInteractions(service);
    }

    @Test
    void readsAndGetsAreLeftAlone() throws Exception {
        MockHttpServletRequest get = request("k1", "");
        get.setMethod("GET");

        filter.doFilter(get, new MockHttpServletResponse(), controller(200, new AtomicInteger(), new AtomicReference<>()));

        verifyNoInteractions(service);
    }

    @Test
    void overlongKeyIsRejected() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request("k".repeat(IdempotencyFilter.MAX_KEY_LENGTH + 1), BODY), response,
                controller(201, calls, new AtomicReference<>()));

        assertThat(calls).hasValue(0);
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).at("/meta/code").asInt())
                .isEqualTo(ErrorConstant.INVALID_PARAMETERS);
    }

    @SuppressWarnings("unused")
    static class SampleController {
        public String create(@RequestBody String body) {
            return body;
        }

        public String createWithOwnKey(@RequestHeader("Idempotency-Key") String key) {
            return key;
        }

        public SseEmitter stream(@RequestBody String body) {
            return new SseEmitter();
        }
    }
}
