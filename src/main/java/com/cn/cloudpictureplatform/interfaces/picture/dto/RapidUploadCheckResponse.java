package com.cn.cloudpictureplatform.interfaces.picture.dto;

import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 快速上传检测响应（秒传接口）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RapidUploadCheckResponse {

    /**
     * 文件是否已存在
     */
    private boolean exists;

    /**
     * 如果存在，返回已有的图片ID
     */
    private UUID existingPictureId;

    /**
     * 文件大小
     */
    private Long sizeBytes;

    /**
     * 可访问的 URL
     */
    private String url;

    /**
     * 图片宽度
     */
    private Integer width;

    /**
     * 图片高度
     */
    private Integer height;

    /**
     * 上传建议
     */
    private String message;

    /**
     * 创建存在的响应
     */
    public static RapidUploadCheckResponse exists(UUID pictureId, String url,
                                                   Long sizeBytes, Integer width, Integer height) {
        return RapidUploadCheckResponse.builder()
                .exists(true)
                .existingPictureId(pictureId)
                .url(url)
                .sizeBytes(sizeBytes)
                .width(width)
                .height(height)
                .message("File already exists, rapid upload available")
                .build();
    }

    /**
     * 创建不存在的响应
     */
    public static RapidUploadCheckResponse notExists() {
        return RapidUploadCheckResponse.builder()
                .exists(false)
                .message("File not found, please upload normally")
                .build();
    }
}
