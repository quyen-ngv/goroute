package com.ds.goroute.controller;

import com.ds.goroute.constant.RequestKeyConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.BasicRequestList;
import com.ds.goroute.exception.BusinessError;
import com.ds.goroute.utils.ErrorMessages;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * The response-envelope helpers every controller uses.
 *
 * <p>Controllers used to extend {@code BaseService}, which is annotated {@code @Service}
 * and injects an {@code HttpServletResponse} plus two dozen failure overloads none of
 * them call. This exposes only what a controller actually needs, and does not make a
 * controller an instance of a service.
 */
public abstract class BaseController {

    @Autowired
    protected HttpServletRequest httpServletRequest;

    protected String getRequestId() {
        Object requestId = httpServletRequest.getAttribute(RequestKeyConstant.REQUEST_ID);
        return requestId == null ? null : requestId.toString();
    }

    protected <T> BaseResponse<T> ofSucceeded(T data) {
        return BaseResponse.ofSucceeded(getRequestId(), data);
    }

    protected <T> BaseResponse<T> ofGetListSucceeded(T data, BasicRequestList requestList) {
        return BaseResponse.ofGetListSucceeded(data, requestList);
    }

    protected BaseResponse ofFailed(BusinessError error) {
        return BaseResponse.ofFailed(getRequestId(), error, ErrorMessages.of(error));
    }

    protected BusinessError getBusinessError(int errorCode) {
        return BusinessError.builder()
                .code(errorCode)
                .message(ErrorMessages.of(errorCode))
                .build();
    }
}
