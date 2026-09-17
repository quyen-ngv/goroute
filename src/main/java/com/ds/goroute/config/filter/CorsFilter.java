package com.ds.goroute.config.filter;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.constant.UrlConstant;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.multipart.MultipartResolver;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;

import java.util.UUID;

import static com.ds.goroute.constant.RequestKeyConstant.THREAD_REQUEST_ID;
import static com.ds.goroute.constant.RequestKeyConstant.X_REQUEST_ID;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorsFilter implements Filter {

    /**
     * Hard ceiling on the non-multipart body this filter will hold in memory. Uploads go
     * through the multipart branch and keep their own 50MB/100MB limits; what lands here
     * is JSON, so 10MB is far above anything the app legitimately posts and far below
     * what it takes to exhaust the heap.
     */
    private static final int MAX_BUFFERED_BODY_CHARS = ApiKeyVerifyRequestWrapper.DEFAULT_MAX_BODY_CHARS;

    private final MultipartResolver multipartResolver;

    public CorsFilter(MultipartResolver multipartResolver) {
        this.multipartResolver = multipartResolver;
    }

    public CorsFilter() {
        this.multipartResolver = new StandardServletMultipartResolver();
    }


    @SneakyThrows
    @SuppressWarnings("unchecked")
    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        HttpServletResponse response = (HttpServletResponse) res;
        HttpServletRequest request = (HttpServletRequest) req;
        // Check uri
        if (request.getRequestURI().contains(UrlConstant.HEALTH_CHECK_URL)) {
            chain.doFilter(req, res);
            return;
        }
        boolean isMultipart = multipartResolver.isMultipart(request);
        String requestId = "";

        try {
            if (isMultipart) {
                MultipartHttpServletRequest multipartRequest = multipartResolver.resolveMultipart(request);

                requestId = multipartRequest.getHeader(X_REQUEST_ID);
                if (requestId == null || requestId.isEmpty()) {
                    requestId = UUID.randomUUID().toString();
                }
                ThreadContext.put(THREAD_REQUEST_ID, requestId);

//                multipartRequest.setAttribute();
//                multipartRequest.setAttribute(CommonJsonKey.OPS_USER_NAME,
//                        accessTokenRequestDto.getUser_name());
                multipartRequest.setAttribute(RequestKeyConstant.REQUEST_BODY, multipartRequest);
                multipartRequest.setAttribute(RequestKeyConstant.REQUEST_ID, requestId);
                chain.doFilter(multipartRequest, response);
                ThreadContext.clearAll();
            } else {
                // This filter sits in front of Spring Security, so the caller may be
                // anonymous. Refuse an oversized body on its declared length before
                // reading a byte of it; a body that lies about its length (or declares
                // none) is cut off by the wrapper's own ceiling below.
                long declaredLength = request.getContentLengthLong();
                if (declaredLength > MAX_BUFFERED_BODY_CHARS) {
                    log.warn("Request body too large to buffer: {} bytes declared, limit {}",
                            declaredLength, MAX_BUFFERED_BODY_CHARS);
                    writeRequestTooLarge(response);
                    ThreadContext.clearAll();
                    return;
                }

                ApiKeyVerifyRequestWrapper requestWrapper =
                        new ApiKeyVerifyRequestWrapper(request, MAX_BUFFERED_BODY_CHARS);
                JSONParser parser = new JSONParser();

                String body = requestWrapper.getBody();
                JSONObject dataRequest;

                // Check if body is empty or not valid JSON
                if (ObjectUtils.isEmpty(body) || body.trim().isEmpty()) {
                    dataRequest = new JSONObject();
                } else {
                    try {
                        dataRequest = (JSONObject) parser.parse(body);
                    } catch (Exception parseEx) {
                        // Never the body itself: a failed login posts its password here.
                        log.warn("Failed to parse request body as JSON, using empty object. "
                                        + "Content-Type: {}, length: {}",
                                request.getContentType(), body.length());
                        dataRequest = new JSONObject();
                    }
                }

                requestId = requestWrapper.getHeader(X_REQUEST_ID);
                if (requestId == null || requestId.isEmpty()) {
                    requestId = UUID.randomUUID().toString();
                }
                ThreadContext.put(THREAD_REQUEST_ID, requestId);
                dataRequest.put(RequestKeyConstant.REQUEST_ID, requestId);
                request.setAttribute(RequestKeyConstant.REQUEST_ID, requestId);

                dataRequest.put(RequestKeyConstant.API_KEY, requestWrapper.getHeader(RequestKeyConstant.X_API_KEY));
                request.setAttribute(RequestKeyConstant.API_KEY, requestWrapper.getHeader(RequestKeyConstant.X_API_KEY));

                dataRequest.put(RequestKeyConstant.API_SECRET, requestWrapper.getHeader(RequestKeyConstant.X_API_SECRET));
                request.setAttribute(RequestKeyConstant.API_SECRET, requestWrapper.getHeader(RequestKeyConstant.X_API_SECRET));

                dataRequest.put(RequestKeyConstant.URI, request.getRequestURI());
                request.setAttribute(RequestKeyConstant.URI, requestWrapper.getHeader(RequestKeyConstant.URI));

                request.setAttribute(RequestKeyConstant.REQUEST_PARAMETERS, req.getParameterMap());
                request.setAttribute(RequestKeyConstant.REQUEST_BODY, dataRequest);
                requestWrapper.setBody(dataRequest.toString());

                chain.doFilter(requestWrapper, res);
                ThreadContext.clearAll();
            }
        } catch (Exception e) {
            Throwable rootCause = e;
            while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
                rootCause = rootCause.getCause();
            }
            String causeClassName = rootCause.getClass().getName();
            if (e instanceof org.springframework.web.multipart.MaxUploadSizeExceededException
                    || rootCause instanceof ApiKeyVerifyRequestWrapper.BodyTooLargeException
                    || causeClassName.contains("SizeLimitExceededException")
                    || causeClassName.contains("MaxUploadSizeExceededException")) {
                log.warn("Request size limit exceeded: {}", e.getMessage(), e);
                writeRequestTooLarge(response);
            } else {
                log.error(e.toString(), e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            }
            ThreadContext.clearAll();
        }
    }

    /**
     * The app's existing "too large" answer, reused so an oversized JSON body looks the
     * same to a client as an oversized upload.
     */
    private void writeRequestTooLarge(HttpServletResponse response) throws java.io.IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"meta\":{\"code\":4131001,\"message\":\"File size exceeds the maximum allowed limit\"}}");
    }
}