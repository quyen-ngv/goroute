package com.ds.goroute.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.http.HttpStatus;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class BusinessError implements Serializable {

    private static final long serialVersionUID = 2405172041950251807L;

    private int code;

    private String message;

    private HttpStatus httpStatus;

    private Object data;

    public BusinessError(int code, HttpStatus httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BusinessError(int code) {
        this.code = code;
    }

    public BusinessError(int code, HttpStatus httpStatus, Object data) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.data = data;
    }

    public BusinessError(int code, Object data) {
        this.code = code;
        this.data = data;
    }

    public BusinessError(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public BusinessError(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}