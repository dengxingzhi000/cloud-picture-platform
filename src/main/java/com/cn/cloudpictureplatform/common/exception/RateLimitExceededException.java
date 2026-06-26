package com.cn.cloudpictureplatform.common.exception;

import com.cn.cloudpictureplatform.common.web.ApiErrorCode;

public class RateLimitExceededException extends ApiException {
    public RateLimitExceededException(String message) {
        super(ApiErrorCode.RATE_LIMITED, message);
    }
}
