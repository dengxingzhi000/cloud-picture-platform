package com.cn.cloudpictureplatform.application.picture;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 相似图片检测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilarPictureResult {

    /**
     * 图片ID
     */
    private UUID pictureId;

    /**
     * 文件内容ID
     */
    private UUID fileContentId;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 图片URL
     */
    private String url;

    /**
     * 相似度分数（0-100）
     */
    private int similarityScore;

    /**
     * 汉明距离（0-64，越小越相似）
     */
    private int hammingDistance;

    /**
     * 文件大小
     */
    private Long sizeBytes;

    /**
     * 图片宽度
     */
    private Integer width;

    /**
     * 图片高度
     */
    private Integer height;
}
