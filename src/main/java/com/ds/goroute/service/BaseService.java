package com.ds.goroute.service;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.BasicRequestList;
import com.ds.goroute.dto.ErrorViolation;
import com.ds.goroute.exception.BusinessError;
import com.ds.goroute.exception.BusinessException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.Enumeration;
import java.util.List;
import java.util.ResourceBundle;

@Service
@Slf4j
public class BaseService {

    @Autowired
    protected Environment env;

    @Value("${spring.application.name}")
    protected String appName;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected HttpServletRequest httpServletRequest;

    @Autowired
    protected HttpServletResponse httpServletResponse;

    public String getMessage(String key) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle("i18n/errors", LocaleContextHolder.getLocale());
            return bundle.getString(key);
        } catch (Exception e) {
            return key;
        }
    }

    public String getMessage(int code) {
        return getMessage(String.valueOf(code));
    }

    public String getMessage(BusinessError error) {
        return ObjectUtils.isEmpty(error.getMessage()) ? getMessage(error.getCode()) : error.getMessage();
    }

    public Object getRequestValue(String key) {
        return getRequestValue(key, Object.class);
    }

    public <T> T getRequestValue(String key, Class<T> clazz) {
        return clazz.cast(httpServletRequest.getAttribute(key));
    }

    public String getRequestStringValue(String key) {
        return getRequestValue(key, String.class);
    }

    public String getRequestId() {
        return getRequestValue(RequestKeyConstant.REQUEST_ID).toString();
    }

    public Object getRequestBody() {
        return getRequestValue(RequestKeyConstant.REQUEST_BODY);
    }

    public <T> BaseResponse<T> ofSucceeded(T data) {
        return BaseResponse.ofSucceeded(getRequestId(), data);
    }

    public <T> BaseResponse<T> ofGetListSucceeded(T data, BasicRequestList requestList) {
        return BaseResponse.ofGetListSucceeded(data, requestList);
    }

    public <T> BaseResponse<T> ofFailed(BusinessException exception, T data) {
        return BaseResponse.ofFailed(getRequestId(), exception, data);
    }

    public <T> BaseResponse<T> ofFailed(BusinessError error, String message, T data) {
        return BaseResponse.ofFailed(getRequestId(), error, message, data);
    }

    public <T> BaseResponse<T> ofFailed(BusinessError error, String message, List<ErrorViolation> errors, T data) {
        return BaseResponse.ofFailed(getRequestId(), error, message, errors, data);
    }

    public BaseResponse ofFailed(BusinessException exception) {
        return BaseResponse.ofFailed(getRequestId(), exception);
    }

    public BaseResponse ofFailed(BusinessError error, String message) {
        return BaseResponse.ofFailed(getRequestId(), error, message);
    }

    public BaseResponse ofFailed(BusinessError error) {
        return BaseResponse.ofFailed(getRequestId(), error, getMessage(error));
    }

    public BaseResponse ofFailed(BusinessError error, String message, List<ErrorViolation> errors) {
        return BaseResponse.ofFailed(getRequestId(), error, message, errors);
    }

    public BaseResponse ofFailed(int errorCode) {
        return BaseResponse.ofFailed(getRequestId(), getBusinessError(errorCode));
    }

    public <T> BaseResponse<T> ofFailed(int errorCode, T data) {
        return BaseResponse.ofFailed(getRequestId(), getBusinessError(errorCode), data);
    }

    public BusinessError getBusinessError(int errorCode) {
        return BusinessError.builder()
                .code(errorCode)
                .message(getMessage(errorCode))
                .build();
    }

    public HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = httpServletRequest.getHeader(headerName);
            headers.add(headerName, headerValue);
        }
        return headers;
    }

    public int getErrorCode(String errorCode, int errorCodeDefault) {
        try {
            return Integer.parseInt(errorCode);
        } catch (NumberFormatException e) {
            return errorCodeDefault;
        }
    }
}