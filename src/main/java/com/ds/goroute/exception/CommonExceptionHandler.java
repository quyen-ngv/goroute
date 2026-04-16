package com.ds.goroute.exception;

import com.ds.goroute.annotations.LogsActivityAnnotation;
import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.ErrorViolation;
import com.ds.goroute.service.BaseService;
import com.google.common.base.CaseFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Controller;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.client.HttpStatusCodeException;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@ControllerAdvice
@Slf4j
public class CommonExceptionHandler extends BaseService {

    @ExceptionHandler(BusinessException.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleBusinessException(BusinessException exception) {
        exception.getError().setMessage(getMessage(exception.getError()));
        Object dataException = exception.getError().getData();
        BaseResponse<?> data = ofFailed(exception);
        if(!ObjectUtils.isEmpty(dataException) && dataException instanceof String) {
            data.getMeta().setErrors(Collections.singletonList(
                ErrorViolation.builder().description((String) dataException).build()));
        }
        return new ResponseEntity<>(data, exception.getError().getHttpStatus() == null
                ? HttpStatus.OK : exception.getError().getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception) {
        List<ErrorViolation> errors = exception.getBindingResult().getFieldErrors().stream()
            .map(e -> ErrorViolation.builder()
                .field(CaseFormat.UPPER_CAMEL.to(CaseFormat.LOWER_UNDERSCORE, e.getField()))
                .code(Integer.toString(getErrorCode(e.getDefaultMessage(), ErrorConstant.INVALID_PARAMETERS)))
                .description(getMessage(e.getDefaultMessage()))
                .build())
            .collect(Collectors.toList());

        BusinessError error = getBusinessError(ErrorConstant.INVALID_PARAMETERS);
        BaseResponse<?> data = ofFailed(error, getMessage(error), errors);
        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleMethodArgumentNotValidException(MissingServletRequestParameterException exception) {
        BusinessError error = getBusinessError(ErrorConstant.INVALID_PARAMETERS);
        BaseResponse<?> data = ofFailed(error,"Param " + exception.getParameterName() + " is required");
        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    @ExceptionHandler(Exception.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleException(Exception exception) {
        BusinessError error = getBusinessError(ErrorConstant.INTERNAL_SERVER_ERROR);
        BaseResponse<?> data = ofFailed(error, getMessage(error), null);
        return new ResponseEntity<>(data, HttpStatus.OK);
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleException(HttpMessageNotReadableException exception) {
        BusinessError error = getBusinessError(ErrorConstant.INVALID_PARAMETERS);
        BaseResponse<?> data = ofFailed(error,"invalid parameter");
        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleIllegalArgumentException(IllegalArgumentException exception) {
        BusinessError error = getBusinessError(ErrorConstant.INVALID_PARAMETERS);
        BaseResponse<?> data = ofFailed(error, getMessage(error), exception.getMessage());
        return new ResponseEntity<>(data, HttpStatus.OK);
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    @LogsActivityAnnotation
    public ResponseEntity<BaseResponse<?>> handleHttpStatusCodeException(HttpStatusCodeException exception) {
        HttpStatus statusCode = (HttpStatus) exception.getStatusCode();

        if (statusCode.value() == HttpStatus.BAD_REQUEST.value()) {
            BusinessError error = getBusinessError(ErrorConstant.INVALID_PARAMETERS);
            BaseResponse<?> data = ofFailed(error, getMessage(error), exception.getMessage());
            return new ResponseEntity<>(data, HttpStatus.OK);
        }

        if (statusCode.value() == HttpStatus.UNAUTHORIZED.value()) {
            BusinessError error = getBusinessError(ErrorConstant.UNAUTHORIZED);
            BaseResponse<?> data = ofFailed(error, getMessage(error), exception.getMessage());
            return new ResponseEntity<>(data, HttpStatus.OK);
        }

        if (statusCode.value() == HttpStatus.FORBIDDEN.value()) {
            BusinessError error = getBusinessError(ErrorConstant.FORBIDDEN_ERROR);
            BaseResponse<?> data = ofFailed(error, getMessage(error), exception.getMessage());
            return new ResponseEntity<>(data, HttpStatus.OK);
        }

        if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
            BusinessError error = getBusinessError(ErrorConstant.NOT_FOUND);
            BaseResponse<?> data = ofFailed(error, getMessage(error), exception.getMessage());
            return new ResponseEntity<>(data, HttpStatus.OK);
        }

        if (statusCode.value() == HttpStatus.INTERNAL_SERVER_ERROR.value()) {
            BusinessError error = getBusinessError(ErrorConstant.INTERNAL_SERVER_ERROR);
            BaseResponse<?> data = ofFailed(error, getMessage(error), exception.getMessage());
            return new ResponseEntity<>(data, HttpStatus.OK);
        }

        throw exception;
    }
}