package com.ds.goroute.config;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.Meta;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class ApiSecurityErrorResponseWriter {

    private ApiSecurityErrorResponseWriter() {
    }

    static void write(
            ObjectMapper objectMapper,
            HttpServletRequest request,
            HttpServletResponse response,
            int httpStatus,
            int errorCode,
            String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }

        Object requestIdAttribute = request.getAttribute(RequestKeyConstant.REQUEST_ID);
        String requestId = requestIdAttribute == null ? null : requestIdAttribute.toString();
        BaseResponse<Void> payload = new BaseResponse<>(new Meta(requestId, errorCode, message), null);

        response.setStatus(httpStatus);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), payload);
    }
}
