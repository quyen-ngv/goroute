package com.ds.goroute.dto;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessError;
import com.ds.goroute.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuperBuilder
public class BaseResponse<T> {

    private Meta meta = new Meta();

    private T data;

    public BaseResponse(Meta meta, T data) {
        this.meta = meta;
        this.data = data;
    }

    public static void verifyMetaResponse(BaseResponse<?> baseResponse, HttpStatus httpStatus, int status) {
        if (Objects.isNull(baseResponse) || Objects.isNull(baseResponse.meta)) {
            return;
        }

        Meta meta = baseResponse.meta;
        if (status != meta.getCode()) {
            throw new BusinessException(meta.getCode(), meta.getMessage(), httpStatus);
        }
    }

    public static <T> BaseResponse<T> ofSucceeded(T data) {
        BaseResponse<T> response = new BaseResponse<>();
        response.data = data;
        response.meta.setCode(ErrorConstant.SUCCESS);
        response.meta.setMessage("OK");
        return response;
    }

    public static <T> BaseResponse<T> ofSucceeded() {
        BaseResponse<T> response = new BaseResponse<>();
        response.meta.setCode(ErrorConstant.SUCCESS);
        return response;
    }

    public static BaseResponse ofFailed(BusinessError error) {
        return ofFailed(error, null);
    }

    public static <T> BaseResponse<T> ofFailed(BusinessError error, T data) {
        return ofFailed(error, null, data);
    }

    public static BaseResponse ofFailed(BusinessError error, String message) {
        return ofFailed(error, message, null);
    }

    public static <T> BaseResponse<T> ofFailed(BusinessError error, String message, T data) {
        return ofFailed(error, message, null, data);
    }

    public static BaseResponse ofFailed(BusinessError error, String message, List<ErrorViolation> errors) {
        BaseResponse response = new BaseResponse<>();
        response.meta.setCode(error.getCode());
        response.meta.setMessage((message != null) ? message : error.getMessage());
        response.meta.setErrors((errors != null) ? new ArrayList<>(errors) : null);
        if(errors != null && errors.size() > 0) {
            response.meta.setMessage(errors.get(0).getDescription());
        }
        return response;
    }

    public static <T> BaseResponse<T> ofFailed(BusinessError error, String message, List<ErrorViolation> errors, T data) {
        BaseResponse<T> response = new BaseResponse<>();
        response.meta.setCode(error.getCode());
        response.meta.setMessage((message != null) ? message : error.getMessage());
        response.meta.setErrors((errors != null) ? new ArrayList<>(errors) : null);
        if(errors != null && errors.size() > 0) {
            response.meta.setMessage(errors.get(0).getDescription());
        }
        response.data = data;
        return response;
    }

    public static BaseResponse ofFailed(BusinessException exception) {
        return ofFailed(exception.getError(), exception.getMessage());
    }

    public static <T> BaseResponse<T> ofFailed(BusinessException exception, T data) {
        return ofFailed(exception.getError(), exception.getMessage(), data);
    }

    public static <T> BaseResponse<T> ofSucceeded(String requestId, T data) {
        BaseResponse<T> response = ofSucceeded(data);
        response.meta.setRequestId(requestId);
        return response;
    }
    public static <T> BaseResponse<T> ofGetListSucceeded(T data, BasicRequestList requestList) {
        BaseResponse<T> response = ofSucceeded(data);
        response.meta.setRequestId(requestList.getRequestId());
        response.meta.setPageSize(requestList.getPageSize());
        response.meta.setPageIndex(requestList.getPageIndex());
        response.meta.setTotalItems(requestList.getTotalItems());
        response.data = data;
        return response;
    }
    public static <T> BaseResponse<T> ofSucceeded(BasicRequest request, T data) {
        BaseResponse<T> response = ofSucceeded(data);
        response.meta.setRequestId(request.getRequestId());
        return response;
    }

    public static <T> BaseResponse<T> ofSucceeded(String requestId) {
        BaseResponse<T> response = ofSucceeded();
        response.meta.setRequestId(requestId);
        return response;
    }

    public static BaseResponse ofFailed(String requestId, BusinessError error) {
        BaseResponse response = ofFailed(error);
        response.meta.setRequestId(requestId);
        return response;
    }

    public static BaseResponse ofFailed(String requestId, BusinessError error, String message) {
        BaseResponse response = ofFailed(error, message);
        response.meta.setRequestId(requestId);
        return response;
    }

    public static <T> BaseResponse<T> ofFailed(String requestId, BusinessError error, T data) {
        BaseResponse<T> response = ofFailed(error, data);
        response.meta.setRequestId(requestId);
        response.data = data;
        return response;
    }

    public static <T> BaseResponse<T> ofFailed(String requestId, BusinessError error, String message, T data) {
        BaseResponse<T> response = ofFailed(error, message, data);
        response.meta.setRequestId(requestId);
        response.data = data;
        return response;
    }

    public static BaseResponse ofFailed(String requestId, BusinessError error, String message, List<ErrorViolation> errors) {
        BaseResponse response = ofFailed(error, message, errors);
        response.meta.setRequestId(requestId);
        return response;
    }

    public static <T> BaseResponse<T> ofFailed(String requestId, BusinessError error, String message, List<ErrorViolation> errors, T data) {
        BaseResponse<T> response = ofFailed(error, message, errors, data);
        response.meta.setRequestId(requestId);
        response.data = data;
        return response;
    }

    public static BaseResponse ofFailed(String requestId, BusinessException exception) {
        BaseResponse response = ofFailed(exception);
        response.meta.setRequestId(requestId);
        return response;
    }

    public static <T> BaseResponse<T> ofFailed(String requestId, BusinessException exception, T data) {
        BaseResponse<T> response = ofFailed(exception, data);
        response.meta.setRequestId(requestId);
        response.data = data;
        return response;
    }
}