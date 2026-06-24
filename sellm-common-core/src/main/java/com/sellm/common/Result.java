package com.sellm.common;

public class Result<T> {
    private final String code;
    private final String message;
    private final T data;

    private Result(String code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.getCode(), ErrorCode.OK.getMessage(), data);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /** 业务异常携带的具体提示(覆盖 ErrorCode 默认文案);message 为空时回退默认文案。 */
    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        String msg = (message == null || message.isBlank()) ? errorCode.getMessage() : message;
        return new Result<>(errorCode.getCode(), msg, null);
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }
}
