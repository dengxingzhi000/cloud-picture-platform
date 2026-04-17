package com.cn.cloudpictureplatform.domain.storage;

import lombok.Builder;
import lombok.Value;

/**
 * 图片哈希计算结果
 */
@Value
@Builder
public class ImageHashResult {

    /**
     * SHA-256 内容哈希（精确匹配）
     */
    String sha256Hash;

    /**
     * 感知哈希 pHash（相似匹配）
     */
    String perceptualHash;

    /**
     * 差异哈希 dHash（相似匹配）
     */
    String diffHash;

    /**
     * 平均哈希 aHash（快速相似匹配）
     */
    String avgHash;

    /**
     * 图片宽度
     */
    Integer width;

    /**
     * 图片高度
     */
    Integer height;

    /**
     * 文件大小
     */
    long sizeBytes;

    /**
     * 内容类型
     */
    String contentType;

    /**
     * 计算两个 pHash 的汉明距离
     * 距离越小，图片越相似
     *
     * @param hash1 第一个哈希
     * @param hash2 第二个哈希
     * @return 汉明距离 (0-64)
     */
    public static int hammingDistance(String hash1, String hash2) {
        if (hash1 == null || hash2 == null || hash1.length() != hash2.length()) {
            return Integer.MAX_VALUE;
        }

        int distance = 0;
        for (int i = 0; i < hash1.length(); i++) {
            char c1 = hash1.charAt(i);
            char c2 = hash2.charAt(i);
            distance += Integer.bitCount(Character.digit(c1, 16) ^ Character.digit(c2, 16));
        }
        return distance;
    }

    /**
     * 判断两个 pHash 是否表示相似的图片
     * 通常汉明距离 <= 10 认为足够相似
     *
     * @param hash1 第一个哈希
     * @param hash2 第二个哈希
     * @param threshold 相似度阈值（默认10）
     * @return 是否相似
     */
    public static boolean isSimilar(String hash1, String hash2, int threshold) {
        return hammingDistance(hash1, hash2) <= threshold;
    }

    /**
     * 判断两个 pHash 是否表示相似的图片（使用默认阈值10）
     */
    public static boolean isSimilar(String hash1, String hash2) {
        return isSimilar(hash1, hash2, 10);
    }
}
