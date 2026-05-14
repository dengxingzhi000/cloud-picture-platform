package com.cn.cloudpictureplatform.common.web;

import lombok.Getter;

@Getter
public enum ApiErrorCode {
    OK("OK", "success"),
    BAD_REQUEST("BAD_REQUEST", "bad request"),
    UNAUTHORIZED("UNAUTHORIZED", "unauthorized"),
    FORBIDDEN("FORBIDDEN", "forbidden"),
    CONFLICT("CONFLICT", "conflict"),
    NOT_FOUND("NOT_FOUND", "not found"),
    SERVER_ERROR("SERVER_ERROR", "server error"),

    // RBAC Management Error Codes
    ROLE_NOT_FOUND("ROLE_NOT_FOUND", "role not found"),
    PERMISSION_NOT_FOUND("PERMISSION_NOT_FOUND", "permission not found"),
    ROLE_ALREADY_EXISTS("ROLE_ALREADY_EXISTS", "role already exists"),
    PERMISSION_ALREADY_EXISTS("PERMISSION_ALREADY_EXISTS", "permission already exists"),
    CANNOT_DELETE_SYSTEM_ROLE("CANNOT_DELETE_SYSTEM_ROLE", "cannot delete system role"),
    CANNOT_DELETE_SYSTEM_PERMISSION("CANNOT_DELETE_SYSTEM_PERMISSION", "cannot delete system permission"),
    CANNOT_REMOVE_LAST_ADMIN("CANNOT_REMOVE_LAST_ADMIN", "cannot remove last admin role"),
    USER_ROLE_ALREADY_ASSIGNED("USER_ROLE_ALREADY_ASSIGNED", "role already assigned to user"),
    USER_ROLE_NOT_FOUND("USER_ROLE_NOT_FOUND", "role not assigned to user"),

    // Space Quota Error Codes
    QUOTA_EXCEEDED("QUOTA_EXCEEDED", "space quota exceeded");

    private final String code;
    private final String defaultMessage;

    ApiErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }
}
