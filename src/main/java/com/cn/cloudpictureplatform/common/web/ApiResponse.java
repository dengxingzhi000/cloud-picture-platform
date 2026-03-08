package com.cn.cloudpictureplatform.common.web;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    private boolean success;
    private String code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> ok() {
        return ApiResponse.ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, ApiErrorCode.OK.getCode(), ApiErrorCode.OK.getDefaultMessage(), data);
    }

    public static <T> ApiResponse<T> error(ApiErrorCode errorCode, String message) {
        return new ApiResponse<>(false, errorCode.getCode(), message, null);
    }
}
