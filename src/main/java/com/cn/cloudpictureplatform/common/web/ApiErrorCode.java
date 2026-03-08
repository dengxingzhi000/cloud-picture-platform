package com.cn.cloudpictureplatform.common.web;

import lombok.Getter;

@Getter
public enum ApiErrorCode {
    OK("OK", "success"),
    BAD_REQUEST("BAD_REQUEST", "bad request"),
    UNAUTHORIZED("UNAUTHORIZED", "unauthorized"),
    FORBIDDEN("FORBIDDEN", "forbidden"),
    NOT_FOUND("NOT_FOUND", "not found"),
    SERVER_ERROR("SERVER_ERROR", "server error");

    private final String code;
    private final String defaultMessage;

    ApiErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
