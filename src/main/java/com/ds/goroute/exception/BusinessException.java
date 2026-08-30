package com.ds.goroute.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

/**
 * Carries a {@link BusinessError} alongside the usual exception message.
 *
 * <p>Deliberately has no Lombok builder: the generated all-args constructor would set
 * the final error field without ever calling {@code super(message)}, so every exception
 * built that way would report a null message.
 */
@Getter
public class BusinessException extends RuntimeException implements Serializable {

    private static final long serialVersionUID = 1905122041950251207L;

    private final BusinessError error;

    public BusinessException(int code, String message, HttpStatus status) {
        super(message);
        this.error = new BusinessError(code, message, status);
    }

    public BusinessException(int code, HttpStatus status) {
        this.error = new BusinessError(code, status);
    }

    public BusinessException(int code) {
        this.error = new BusinessError(code);
    }

    public BusinessException(int code, Object data) {
        this.error = new BusinessError(code, data);
    }

    public BusinessException(int code, String message) {
        super(message);
        this.error = new BusinessError(code, message);
    }

    public BusinessException(BusinessError error) {
        super(error.getMessage());
        this.error = error;
    }

    public BusinessException(BusinessError error, String message) {
        super(message);
        this.error = error;
    }

    public BusinessException(BusinessError error, String message, Throwable cause) {
        super(message, cause);
        this.error = error;
    }
}
