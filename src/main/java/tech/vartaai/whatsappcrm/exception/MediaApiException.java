package tech.vartaai.whatsappcrm.exception;

import org.springframework.http.HttpStatus;

public class MediaApiException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public MediaApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
