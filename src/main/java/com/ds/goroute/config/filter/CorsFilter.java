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
                ApiKeyVerifyRequestWrapper requestWrapper = new ApiKeyVerifyRequestWrapper(request);
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
                        log.warn("Failed to parse request body as JSON, using empty object. Body: {}", body);
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
            log.error(e.toString(), e);
            response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
            ThreadContext.clearAll();
        }
    }
}