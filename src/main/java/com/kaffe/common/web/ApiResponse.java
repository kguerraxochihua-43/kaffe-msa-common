package com.kaffe.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String message;
    private T data;
    private String errorCode;
    private Map<String, String> errors;
    private String traceId;
    private String timestamp;

    public ApiResponse() {
        this.timestamp = now();
    }

    private ApiResponse(
            boolean success,
            String message,
            T data,
            String errorCode,
            Map<String, String> errors,
            String traceId
    ) {
        this.success = success;
        this.message = message;
        this.data = data;
        this.errorCode = errorCode;
        this.errors = errors;
        this.traceId = traceId;
        this.timestamp = now();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return success(message, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null, null);
    }

    public static <T> ApiResponse<T> success(String message) {
        return new ApiResponse<>(true, message, null, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message) {
        return new ApiResponse<>(false, message, null, null, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, null, errorCode, null, null);
    }

    public static <T> ApiResponse<T> error(String message, String errorCode, String traceId) {
        return new ApiResponse<>(false, message, null, errorCode, null, traceId);
    }

    public static <T> ApiResponse<T> validationError(String message, Map<String, String> errors, String traceId) {
        return new ApiResponse<>(false, message, null, "VALIDATION_ERROR", errors, traceId);
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public Map<String, String> getErrors() {
        return errors;
    }

    public void setErrors(Map<String, String> errors) {
        this.errors = errors;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    public static final class Builder<T> {
        private boolean success;
        private String message;
        private T data;
        private String errorCode;
        private Map<String, String> errors;
        private String traceId;

        private Builder() {
        }

        public Builder<T> success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder<T> errors(Map<String, String> errors) {
            this.errors = errors;
            return this;
        }

        public Builder<T> traceId(String traceId) {
            this.traceId = traceId;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(success, message, data, errorCode, errors, traceId);
        }
    }
}
