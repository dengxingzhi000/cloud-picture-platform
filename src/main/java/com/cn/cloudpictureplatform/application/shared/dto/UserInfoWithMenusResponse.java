package com.cn.cloudpictureplatform.application.shared.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.cn.cloudpictureplatform.application.auth.dto.MenuItemResponse;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserInfoWithMenusResponse {
    private UserInfoResponse userInfo;
    private List<MenuItemResponse> menus;
}