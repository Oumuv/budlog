package dev.oumuv.budlog.common;

import org.springframework.http.HttpStatus;

public class BusinessException extends RuntimeException {

    private final int code;
    private final HttpStatus status;

    public BusinessException(int code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public int getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static BusinessException notFound(String message) {
        return new BusinessException(40400, HttpStatus.NOT_FOUND, message);
    }

    public static BusinessException validation(String message) {
        return new BusinessException(40000, HttpStatus.BAD_REQUEST, message);
    }
}

