package com.cn.cloudpictureplatform.common.exception;

import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private final ApiErrorCode errorCode;

    public ApiException(ApiErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
