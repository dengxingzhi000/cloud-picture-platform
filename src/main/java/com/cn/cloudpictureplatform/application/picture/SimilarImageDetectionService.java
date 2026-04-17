package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.domain.storage.FileContent;
import com.cn.cloudpictureplatform.domain.storage.FileDeduplicationService;
import com.cn.cloudpictureplatform.domain.storage.PerceptualHashService;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 相似图片检测服务
 * 基于感知哈希查找视觉上相似的图片
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SimilarImageDetectionService {

    private final FileDeduplicationService fileDeduplicationService;
    private final PerceptualHashService perceptualHashService;
    private final PictureAssetRepository pictureAssetRepository;

    /**
     * 查找与指定哈希相似的图片
     *
     * @param perceptualHash 感知哈希值
     * @param threshold      相似度阈值（汉明距离，默认10）
     * @param limit          最大返回数量
     * @return 相似图片列表（按相似度排序）
     */
    public List<SimilarPictureResult> findSimilarImages(String perceptualHash, int threshold, int limit) {
        // 获取相同大小的候选图片（优化：按文件大小筛选可大幅减少候选集）
        var candidates = fileDeduplicationService.findSimilarImages(
                perceptualHash,
                threshold,
                PageRequest.of(0, Math.max(limit * 10, 100))
        );

        List<SimilarPictureResult> results = new ArrayList<>();

        for (FileContent file : candidates) {
            if (file.getPerceptualHash() == null) {
                continue;
            }

            int distance = perceptualHashService.hammingDistance(perceptualHash, file.getPerceptualHash());

            if (distance <= threshold) {
                // 查找引用该文件的所有图片
                var pictureAssets = pictureAssetRepository.findByFileContentId(file.getId());

                for (var asset : pictureAssets) {
                    results.add(SimilarPictureResult.builder()
                            .pictureId(asset.getId())
                            .fileContentId(file.getId())
                            .name(asset.getName())
                            .url(asset.getUrl())
                            .similarityScore(100 - distance * 100 / 64)  // 转换为百分比
                            .hammingDistance(distance)
                            .sizeBytes(file.getSizeBytes())
                            .width(file.getWidth())
                            .height(file.getHeight())
                            .build());
                }
            }
        }

        // 按相似度排序并限制数量
        return results.stream()
                .sorted((a, b) -> Integer.compare(a.getHammingDistance(), b.getHammingDistance()))
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * 判断两张图片是否相似
     *
     * @param hash1 第一张图片的 pHash
     * @param hash2 第二张图片的 pHash
     * @return 是否相似（使用默认阈值）
     */
    public boolean isSimilar(String hash1, String hash2) {
        return perceptualHashService.isSimilar(hash1, hash2);
    }

    /**
     * 计算汉明距离
     */
    public int calculateHammingDistance(String hash1, String hash2) {
        return perceptualHashService.hammingDistance(hash1, hash2);
    }
}
