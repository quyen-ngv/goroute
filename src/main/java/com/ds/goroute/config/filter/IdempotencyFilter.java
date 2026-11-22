package com.ds.goroute.config.filter;

import com.ds.goroute.config.ApiSecurityErrorResponseWriter;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.service.idempotency.IdempotencyService;
import com.ds.goroute.service.idempotency.IdempotencyService.Claim;
import com.ds.goroute.utils.ErrorMessages;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.ServletRequestPathUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;

/**
 * Generic {@code Idempotency-Key} support for authenticated mutating requests, Stripe style.
 *
 * <p>Applies only when all of these hold; anything else passes through untouched, so a client
 * that never sends the header sees no change:
 * <ul>
 *   <li>POST, PUT, PATCH or DELETE under {@code /v1/api/}, with a non-blank header;</li>
 *   <li>the caller is an authenticated user (the key is scoped to the user id);</li>
 *   <li>the body is JSON, text or absent; multipart and form bodies are skipped, because
 *       buffering an upload to hash it costs more than it protects;</li>
 *   <li>the body is at most {@link #MAX_HASHED_BODY_BYTES};</li>
 *   <li>the handler does not read {@code Idempotency-Key} itself (hotel bookings, activity
 *       orders, wallet reminders and the AI trip stream keep their own domain idempotency)
 *       and does not answer asynchronously (SSE, DeferredResult, ...).</li>
 * </ul>
 *
 * <p>The first request claims the key and runs; a 2xx or final 4xx response is stored for
 * {@link IdempotencyService#TTL} (30 days) and replayed verbatim (status, content type,
 * body, plus {@code Idempotent-Replayed: true}) to a repeat of the same request. A 5xx or
 * an exception releases the key. A repeat while the first still runs gets 409
 * {@code IDEMPOTENCY_REQUEST_IN_PROGRESS}; the same key on a different method, path, query
 * or body gets 422 {@code IDEMPOTENCY_KEY_REUSED}.
 *
 * <p>Ordered after the Spring Security filter chain (order -100) so the user is known and
 * unauthorized requests never claim a key.
 */
@Component
@Order(IdempotencyFilter.ORDER)
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    /** After Spring Security's FilterChainProxy (-100) and the locale filters (1, 2). */
    public static final int ORDER = 20;

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED_HEADER = "Idempotent-Replayed";
    public static final int MAX_KEY_LENGTH = 255;
    public static final int MAX_HASHED_BODY_BYTES = 1024 * 1024;

    private static final String API_PREFIX = "/v1/api/";
    private static final Set<String> METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final Set<Class<?>> ASYNC_RETURN_TYPES = Set.of(
            ResponseBodyEmitter.class, DeferredResult.class, Callable.class, CompletionStage.class,
            WebAsyncTask.class, StreamingResponseBody.class);

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<HandlerMapping> handlerMapping;

    public IdempotencyFilter(IdempotencyService idempotencyService,
                             ObjectMapper objectMapper,
                             @Qualifier("requestMappingHandlerMapping") ObjectProvider<HandlerMapping> handlerMapping) {
        this.idempotencyService = idempotencyService;
        this.objectMapper = objectMapper;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String key = request.getHeader(HEADER);
        return key == null || key.isBlank()
                || !METHODS.contains(request.getMethod())
                || !pathWithinApplication(request).startsWith(API_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String key = request.getHeader(HEADER).trim();
        if (key.length() > MAX_KEY_LENGTH) {
            ApiSecurityErrorResponseWriter.write(objectMapper, request, response,
                    HttpServletResponse.SC_BAD_REQUEST, ErrorConstant.INVALID_PARAMETERS,
                    HEADER + " must be at most " + MAX_KEY_LENGTH + " characters");
            return;
        }
        UUID userId = currentUserId(request);
        if (userId == null || !hasSupportedBody(request) || declaredTooLarge(request)) {
            chain.doFilter(request, response);
            return;
        }
        HandlerMethod handler = resolveHandler(request);
        if (handler == null || managesItsOwnIdempotency(handler) || answersAsynchronously(handler)) {
            chain.doFilter(request, response);
            return;
        }

        BufferedBody body = readBody(request);
        HttpServletRequest replayable = body.wrap(request);
        if (body.truncated()) {
            // Chunked upload larger than we hash: run it unprotected rather than buffer it all.
            chain.doFilter(replayable, response);
            return;
        }

        String path = pathWithQuery(request);
        String fingerprint = fingerprint(request.getMethod(), path, body.bytes());
        Claim claim = idempotencyService.claim(userId, key, request.getMethod(), path, fingerprint);
        switch (claim) {
            case Claim.Replay replay -> writeReplay(response, replay);
            case Claim.InProgress ignored -> writeError(request, response, HttpServletResponse.SC_CONFLICT,
                    ErrorConstant.IDEMPOTENCY_REQUEST_IN_PROGRESS);
            case Claim.Mismatch ignored -> writeError(request, response, 422,
                    ErrorConstant.IDEMPOTENCY_KEY_REUSED);
            case Claim.Acquired acquired -> runAndRecord(replayable, request, response, chain, acquired.recordId());
        }
    }

    private void runAndRecord(HttpServletRequest replayable, HttpServletRequest original,
                              HttpServletResponse response, FilterChain chain, UUID recordId)
            throws ServletException, IOException {
        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(replayable, wrapped);
        } catch (IOException | ServletException | RuntimeException exception) {
            releaseQuietly(recordId);
            throw exception;
        }
        try {
            if (original.isAsyncStarted() || wrapped.getStatus() >= 500) {
                releaseQuietly(recordId);
            } else {
                idempotencyService.complete(recordId, wrapped.getStatus(), wrapped.getContentType(),
                        wrapped.getContentAsByteArray());
            }
        } catch (RuntimeException exception) {
            // Storing the answer failed; the client still gets it, and may retry the key.
            log.error("Could not store the idempotent response for record {}", recordId, exception);
            releaseQuietly(recordId);
        } finally {
            wrapped.copyBodyToResponse();
        }
    }

    private void releaseQuietly(UUID recordId) {
        try {
            idempotencyService.release(recordId);
        } catch (RuntimeException exception) {
            log.error("Could not release idempotency record {}; it expires on its own", recordId, exception);
        }
    }

    private void writeReplay(HttpServletResponse response, Claim.Replay replay) throws IOException {
        response.setStatus(replay.status());
        if (replay.contentType() != null) {
            response.setContentType(replay.contentType());
        }
        response.setHeader(REPLAYED_HEADER, "true");
        response.setContentLength(replay.body().length);
        response.getOutputStream().write(replay.body());
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, int code)
            throws IOException {
        ApiSecurityErrorResponseWriter.write(objectMapper, request, response, status, code, ErrorMessages.of(code));
    }

    private UUID currentUserId(HttpServletRequest request) {
        if (request.getAttribute(RequestKeyConstant.USER_ID) instanceof UUID userId) {
            return userId;
        }
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException notAUser) {
            // Internal token or job API key: not a user, nothing to scope a key to.
            return null;
        }
    }

    private static boolean hasSupportedBody(HttpServletRequest request) {
        String contentType = request.getContentType();
        if (contentType == null || contentType.isBlank()) {
            return true;
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return MediaType.APPLICATION_JSON.isCompatibleWith(type)
                    || (type.getSubtype() != null && type.getSubtype().endsWith("+json"))
                    || "text".equalsIgnoreCase(type.getType());
        } catch (RuntimeException invalid) {
            return false;
        }
    }

    private static boolean declaredTooLarge(HttpServletRequest request) {
        return request.getContentLengthLong() > MAX_HASHED_BODY_BYTES;
    }

    private HandlerMethod resolveHandler(HttpServletRequest request) {
        HandlerMapping mapping = handlerMapping.getIfAvailable();
        if (mapping == null) {
            return null;
        }
        // The mapping needs the parsed path DispatcherServlet normally caches; put back
        // whatever was there so the real dispatch starts from the same state.
        Object previousPath = request.getAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
        try {
            ServletRequestPathUtils.parseAndCache(request);
            HandlerExecutionChain handlerChain = mapping.getHandler(request);
            return handlerChain != null && handlerChain.getHandler() instanceof HandlerMethod method ? method : null;
        } catch (Exception exception) {
            return null;
        } finally {
            if (previousPath == null) {
                request.removeAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE);
            } else {
                request.setAttribute(ServletRequestPathUtils.PATH_ATTRIBUTE, previousPath);
            }
        }
    }

    static boolean managesItsOwnIdempotency(HandlerMethod handler) {
        for (MethodParameter parameter : handler.getMethodParameters()) {
            RequestHeader header = parameter.getParameterAnnotation(RequestHeader.class);
            if (header != null && (HEADER.equalsIgnoreCase(header.name()) || HEADER.equalsIgnoreCase(header.value()))) {
                return true;
            }
        }
        return false;
    }

    static boolean answersAsynchronously(HandlerMethod handler) {
        Class<?> returnType = handler.getReturnType().getParameterType();
        for (Class<?> async : ASYNC_RETURN_TYPES) {
            if (async.isAssignableFrom(returnType)) {
                return true;
            }
        }
        if (org.springframework.http.ResponseEntity.class.isAssignableFrom(returnType)) {
            Class<?> body = handler.getReturnType().nested().getNestedParameterType();
            return ASYNC_RETURN_TYPES.stream().anyMatch(async -> async.isAssignableFrom(body));
        }
        return false;
    }

    static String fingerprint(String method, String pathWithQuery, byte[] body) {
        String bodyHash = sha256Hex(body);
        return sha256Hex((method + "\n" + pathWithQuery + "\n" + bodyHash).getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String pathWithQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (path == null) {
            return "";
        }
        return contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)
                ? path.substring(contextPath.length())
                : path;
    }

    /** Reads at most one byte past the limit, so an oversized chunked body is never buffered whole. */
    private static BufferedBody readBody(HttpServletRequest request) throws IOException {
        InputStream input = request.getInputStream();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > MAX_HASHED_BODY_BYTES) {
                return new BufferedBody(buffer.toByteArray(), input, true);
            }
        }
        return new BufferedBody(buffer.toByteArray(), null, false);
    }

    private record BufferedBody(byte[] bytes, InputStream rest, boolean truncated) {
        HttpServletRequest wrap(HttpServletRequest request) {
            return new ReplayingBodyRequest(request, bytes, rest);
        }
    }

    /** Serves the bytes already read, then whatever of the original stream is left. */
    private static final class ReplayingBodyRequest extends HttpServletRequestWrapper {
        private final byte[] head;
        private final InputStream rest;

        ReplayingBodyRequest(HttpServletRequest request, byte[] head, InputStream rest) {
            super(request);
            this.head = head;
            this.rest = rest;
        }

        @Override
        public ServletInputStream getInputStream() {
            InputStream source = rest == null
                    ? new ByteArrayInputStream(head)
                    : new SequenceInputStream(new ByteArrayInputStream(head), rest);
            return new ServletInputStream() {
                private boolean finished;

                @Override
                public int read() throws IOException {
                    int value = source.read();
                    finished = value == -1;
                    return value;
                }

                @Override
                public int read(byte[] target, int offset, int length) throws IOException {
                    int count = source.read(target, offset, length);
                    finished = count == -1;
                    return count;
                }

                @Override
                public boolean isFinished() {
                    return finished;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Async reads are not supported");
                }
            };
        }

        @Override
        public java.io.BufferedReader getReader() {
            String encoding = getCharacterEncoding() == null ? StandardCharsets.UTF_8.name() : getCharacterEncoding();
            return new java.io.BufferedReader(new java.io.InputStreamReader(getInputStream(),
                    java.nio.charset.Charset.forName(encoding)));
        }
    }
}
