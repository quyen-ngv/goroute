package com.ds.goroute.exception;

import com.ds.goroute.constant.ErrorConstant;
import com.ds.goroute.dto.BaseResponse;
import com.ds.goroute.dto.ErrorViolation;
import com.ds.goroute.service.BaseService;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class CommonExceptionHandler extends BaseService {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<BaseResponse<?>> handleBusinessException(BusinessException exception) {
        BusinessError error = exception.getError();
        String message = getMessage(error);
        error.setMessage(message);
        HttpStatus status = error.getHttpStatus() == null
                ? statusFromBusinessCode(error.getCode())
                : error.getHttpStatus();
        List<ErrorViolation> violations = error.getData() instanceof String description
                ? List.of(ErrorViolation.builder().description(description).build())
                : null;
        return response(error, message, violations, status);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<BaseResponse<?>> handleBodyValidation(MethodArgumentNotValidException exception) {
        List<ErrorViolation> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> violation(error.getField(), error.getDefaultMessage()))
                .toList();
        return invalidParameters(errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<BaseResponse<?>> handleMethodValidation(HandlerMethodValidationException exception) {
        List<ErrorViolation> errors = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> violation(
                                result.getMethodParameter().getParameterName(),
                                error.getDefaultMessage())))
                .toList();
        return invalidParameters(errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<BaseResponse<?>> handleConstraintViolation(ConstraintViolationException exception) {
        List<ErrorViolation> errors = exception.getConstraintViolations().stream()
                .map(violation -> violation(
                        violation.getPropertyPath().toString(), violation.getMessage()))
                .toList();
        return invalidParameters(errors);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<BaseResponse<?>> handleMissingParameter(MissingServletRequestParameterException exception) {
        return invalidParameters(List.of(violation(
                exception.getParameterName(), "Parameter is required")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<BaseResponse<?>> handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return invalidParameters(List.of(violation(
                exception.getName(), "Parameter has an invalid value")));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<BaseResponse<?>> handleUnreadableBody(HttpMessageNotReadableException exception) {
        return invalidParameters(List.of(violation(null, "Request body is invalid")));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<BaseResponse<?>> handleIllegalArgument(IllegalArgumentException exception) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? "Invalid parameter"
                : exception.getMessage();
        return invalidParameters(List.of(violation(null, message)));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<BaseResponse<?>> handleAuthentication(AuthenticationException exception) {
        return response(ErrorConstant.UNAUTHORIZED, "Authentication is required", null, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<BaseResponse<?>> handleAccessDenied(AccessDeniedException exception) {
        return response(ErrorConstant.FORBIDDEN_ERROR, "Access denied", null, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<BaseResponse<?>> handleResponseStatus(ResponseStatusException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = status.is5xxServerError() ? "Internal server error" : exception.getReason();
        return response(errorCode(status), message, null, status);
    }

    @ExceptionHandler(HttpStatusCodeException.class)
    public ResponseEntity<BaseResponse<?>> handleDownstreamStatus(HttpStatusCodeException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = status.is5xxServerError() ? "Downstream service failed" : "Downstream request was rejected";
        return response(errorCode(status), message, null, status);
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<BaseResponse<?>> handleMaxUploadSize(org.springframework.web.multipart.MaxUploadSizeExceededException exception) {
        return response(ErrorConstant.FILE_TOO_LARGE,
                "File size exceeds the maximum allowed limit", null,
                HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<BaseResponse<?>> handleUnexpectedException(Exception exception) {
        log.error("Unhandled API exception for {} {}",
                httpServletRequest.getMethod(), httpServletRequest.getRequestURI(), exception);
        return response(ErrorConstant.INTERNAL_SERVER_ERROR, "Internal server error", null,
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<BaseResponse<?>> invalidParameters(List<ErrorViolation> errors) {
        return response(ErrorConstant.INVALID_PARAMETERS, "Invalid parameters", errors, HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<BaseResponse<?>> response(
            int code, String message, List<ErrorViolation> errors, HttpStatusCode status) {
        return response(new BusinessError(code, message), message, errors, status);
    }

    private ResponseEntity<BaseResponse<?>> response(
            BusinessError error, String message, List<ErrorViolation> errors, HttpStatusCode status) {
        BaseResponse<?> body = ofFailed(error, message, errors);
        return ResponseEntity.status(status).body(body);
    }

    private ErrorViolation violation(String field, String description) {
        return ErrorViolation.builder()
                .field(toSnakeCase(field))
                .code(Integer.toString(ErrorConstant.INVALID_PARAMETERS))
                .description(description == null ? "Invalid value" : description)
                .build();
    }

    private String toSnakeCase(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String leaf = value.contains(".") ? value.substring(value.lastIndexOf('.') + 1) : value;
        return leaf.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(java.util.Locale.ROOT);
    }

    private HttpStatus statusFromBusinessCode(int code) {
        String digits = Integer.toString(Math.abs(code));
        if (digits.length() >= 3) {
            HttpStatus resolved = HttpStatus.resolve(Integer.parseInt(digits.substring(0, 3)));
            if (resolved != null && resolved.isError()) {
                return resolved;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private int errorCode(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorConstant.INVALID_PARAMETERS;
            case 401 -> ErrorConstant.UNAUTHORIZED;
            case 403 -> ErrorConstant.FORBIDDEN_ERROR;
            case 404 -> ErrorConstant.NOT_FOUND;
            case 409 -> ErrorConstant.ALREADY_PROCESSED;
            default -> status.is5xxServerError()
                    ? ErrorConstant.INTERNAL_SERVER_ERROR
                    : ErrorConstant.BAD_REQUEST;
        };
    }
}
