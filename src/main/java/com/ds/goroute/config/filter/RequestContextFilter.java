package com.ds.goroute.config.filter;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.constant.UrlConstant;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.io.IOException;
import java.util.UUID;

import static com.ds.goroute.constant.RequestKeyConstant.THREAD_REQUEST_ID;
import static com.ds.goroute.constant.RequestKeyConstant.X_REQUEST_ID;

/**
 * First filter of every request: gives it a request id and caps the size of a JSON body.
 *
 * <p>The request id comes from {@code X-Request-ID} or is generated, and is put in the logging
 * MDC and in the {@link RequestKeyConstant#REQUEST_ID} attribute that error responses echo.
 * The body is forwarded byte for byte; this filter used to parse it and write request
 * metadata (including the API secret header) back into it.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RequestContextFilter implements Filter {

    /**
     * Hard ceiling on the non-multipart body held in memory. Uploads go through the multipart
     * branch and keep their own 50MB/100MB limits; what lands here is JSON, so 10MB is far
     * above anything the app legitimately posts and far below what it takes to exhaust the heap.
     */
    private static final int MAX_BUFFERED_BODY_CHARS = ApiKeyVerifyRequestWrapper.DEFAULT_MAX_BODY_CHARS;

    private final MultipartResolver multipartResolver;

    public RequestContextFilter(MultipartResolver multipartResolver) {
        this.multipartResolver = multipartResolver;
    }

    public RequestContextFilter() {
        this.multipartResolver = new StandardServletMultipartResolver();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        if (request.getRequestURI().contains(UrlConstant.HEALTH_CHECK_URL)) {
            chain.doFilter(req, res);
            return;
        }

        try {
            HttpServletRequest forwarded;
            if (multipartResolver.isMultipart(request)) {
                MultipartHttpServletRequest multipartRequest = multipartResolver.resolveMultipart(request);
                forwarded = multipartRequest;
            } else {
                // This filter sits in front of Spring Security, so the caller may be
                // anonymous. Refuse an oversized body on its declared length before
                // reading a byte of it; a body that lies about its length (or declares
                // none) is cut off by the wrapper's own ceiling.
                long declaredLength = request.getContentLengthLong();
                if (declaredLength > MAX_BUFFERED_BODY_CHARS) {
                    log.warn("Request body too large to buffer: {} bytes declared, limit {}",
                            declaredLength, MAX_BUFFERED_BODY_CHARS);
                    writeRequestTooLarge(response);
                    return;
                }
                forwarded = new ApiKeyVerifyRequestWrapper(request, MAX_BUFFERED_BODY_CHARS);
            }

            String requestId = requestIdOf(forwarded);
            MDC.put(THREAD_REQUEST_ID, requestId);
            forwarded.setAttribute(RequestKeyConstant.REQUEST_ID, requestId);
            chain.doFilter(forwarded, response);
        } catch (Exception e) {
            if (isSizeLimit(e)) {
                log.warn("Request size limit exceeded: {}", e.getMessage(), e);
                writeRequestTooLarge(response);
            } else {
                log.error("Request failed before reaching a controller", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
        } finally {
            MDC.remove(THREAD_REQUEST_ID);
        }
    }

    private static String requestIdOf(HttpServletRequest request) {
        String requestId = request.getHeader(X_REQUEST_ID);
        return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
    }

    private static boolean isSizeLimit(Exception e) {
        Throwable rootCause = e;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String causeClassName = rootCause.getClass().getName();
        return e instanceof org.springframework.web.multipart.MaxUploadSizeExceededException
                || rootCause instanceof ApiKeyVerifyRequestWrapper.BodyTooLargeException
                || causeClassName.contains("SizeLimitExceededException")
                || causeClassName.contains("MaxUploadSizeExceededException");
    }

    /**
     * The app's existing "too large" answer, reused so an oversized JSON body looks the
     * same to a client as an oversized upload.
     */
    private static void writeRequestTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"meta\":{\"code\":4131001,\"message\":\"File size exceeds the maximum allowed limit\"}}");
    }
}
