package com.ds.goroute.dto;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.exception.BusinessError;
import com.ds.goroute.exception.BusinessException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The response envelope every endpoint answers with.
 *
 * <p>The factory methods are overloads over the same four optional pieces — request id,
 * message, violations and payload — so they all funnel into {@link #failure} or
 * {@link #success}. Adding another combination means adding a delegating one-liner, not
 * another copy of the assembly rules.
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
@Slf4j
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuperBuilder
public class BaseResponse<T> {

    @Builder.Default
    private Meta meta = new Meta();

    private T data;

    public BaseResponse(Meta meta, T data) {
        this.meta = meta;
        this.data = data;
    }

    // --- assembly ---------------------------------------------------------------------

    private static <T> BaseResponse<T> success(String requestId, T data, boolean withOkMessage) {
        BaseResponse<T> response = new BaseResponse<>();
        response.data = data;
        response.meta.setRequestId(requestId);
        response.meta.setCode(ErrorConstant.SUCCESS);
        if (withOkMessage) {
            response.meta.setMessage("OK");
        }
        return response;
    }

    private static <T> BaseResponse<T> failure(
            String requestId, BusinessError error, String message,
            List<ErrorViolation> errors, T data) {
        BaseResponse<T> response = new BaseResponse<>();
        response.data = data;
        response.meta.setRequestId(requestId);
        response.meta.setCode(error.getCode());
        response.meta.setErrors(errors != null ? new ArrayList<>(errors) : null);
        // A violation describes the failure more precisely than the generic message, so
        // when one is present it is what the caller is shown.
        response.meta.setMessage(errors != null && !errors.isEmpty()
                ? errors.get(0).getDescription()
                : (message != null ? message : error.getMessage()));
        return response;
    }

    // --- success ----------------------------------------------------------------------

    public static <T> BaseResponse<T> ofSucceeded(T data) {
        return success(null, data, true);
    }

    public static <T> BaseResponse<T> ofSucceeded() {
        return success(null, null, false);
    }

    public static <T> BaseResponse<T> ofSucceeded(String requestId, T data) {
        return success(requestId, data, true);
    }

    public static <T> BaseResponse<T> ofSucceeded(String requestId) {
        return success(requestId, null, false);
    }

    public static <T> BaseResponse<T> ofSucceeded(BasicRequest request, T data) {
        return success(request.getRequestId(), data, true);
    }

    public static <T> BaseResponse<T> ofGetListSucceeded(T data, BasicRequestList requestList) {
        BaseResponse<T> response = success(requestList.getRequestId(), data, true);
        response.meta.setPageSize(requestList.getPageSize());
        response.meta.setPageIndex(requestList.getPageIndex());
        response.meta.setTotalItems(requestList.getTotalItems());
        return response;
    }

    // --- failure ----------------------------------------------------------------------

    public static BaseResponse ofFailed(BusinessError error) {
        return failure(null, error, null, null, null);
    }

    public static <T> BaseResponse<T> ofFailed(BusinessError error, T data) {
        return failure(null, error, null, null, data);
    }

    public static BaseResponse ofFailed(BusinessError error, String message) {
        return failure(null, error, message, null, null);
    }

    public static <T> BaseResponse<T> ofFailed(BusinessError error, String message, T data) {
        return failure(null, error, message, null, data);
    }

    public static BaseResponse ofFailed(BusinessError error, String message, List<ErrorViolation> errors) {
        return failure(null, error, message, errors, null);
    }

    public static <T> BaseResponse<T> ofFailed(
            BusinessError error, String message, List<ErrorViolation> errors, T data) {
        return failure(null, error, message, errors, data);
    }

    public static BaseResponse ofFailed(BusinessException exception) {
        return failure(null, exception.getError(), exception.getMessage(), null, null);
    }

    public static <T> BaseResponse<T> ofFailed(BusinessException exception, T data) {
        return failure(null, exception.getError(), exception.getMessage(), null, data);
    }

    public static BaseResponse ofFailed(String requestId, BusinessError error) {
        return failure(requestId, error, null, null, null);
    }

    public static BaseResponse ofFailed(String requestId, BusinessError error, String message) {
        return failure(requestId, error, message, null, null);
    }

    public static <T> BaseResponse<T> ofFailed(String requestId, BusinessError error, T data) {
        return failure(requestId, error, null, null, data);
    }

    public static <T> BaseResponse<T> ofFailed(
            String requestId, BusinessError error, String message, T data) {
        return failure(requestId, error, message, null, data);
    }

    public static BaseResponse ofFailed(
            String requestId, BusinessError error, String message, List<ErrorViolation> errors) {
        return failure(requestId, error, message, errors, null);
    }

    public static <T> BaseResponse<T> ofFailed(
            String requestId, BusinessError error, String message,
            List<ErrorViolation> errors, T data) {
        return failure(requestId, error, message, errors, data);
    }

    public static BaseResponse ofFailed(String requestId, BusinessException exception) {
        return failure(requestId, exception.getError(), exception.getMessage(), null, null);
    }

    public static <T> BaseResponse<T> ofFailed(String requestId, BusinessException exception, T data) {
        return failure(requestId, exception.getError(), exception.getMessage(), null, data);
    }

    // --- inspection -------------------------------------------------------------------

    public static void verifyMetaResponse(BaseResponse<?> baseResponse, HttpStatus httpStatus, int status) {
        if (Objects.isNull(baseResponse) || Objects.isNull(baseResponse.meta)) {
            return;
        }

        Meta meta = baseResponse.meta;
        if (status != meta.getCode()) {
            throw new BusinessException(meta.getCode(), meta.getMessage(), httpStatus);
        }
    }
}
