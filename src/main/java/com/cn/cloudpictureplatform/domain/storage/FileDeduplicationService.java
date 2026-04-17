package com.cn.cloudpictureplatform.domain.storage;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.cn.cloudpictureplatform.infrastructure.persistence.FileContentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件去重服务 - 管理重复文件检测和存储去重
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeduplicationService {
    private final FileContentRepository fileContentRepository;
    private final PerceptualHashService perceptualHashService;

    /**
     * 查找完全相同的文件（基于 SHA-256）
     *
     * @param sha256Hash 文件的 SHA-256 哈希
     * @return 已存在的文件内容记录
     */
    public Optional<FileContent> findExactDuplicate(String sha256Hash) {
        return fileContentRepository.findBySha256Hash(sha256Hash);
    }

    /**
     * 检测文件是否已存在（精确匹配）
     */
    public boolean existsExactDuplicate(String sha256Hash) {
        return fileContentRepository.existsBySha256Hash(sha256Hash);
    }

    /**
     * 查找相似图片（基于感知哈希）
     *
     * @param perceptualHash 感知哈希值
     * @param threshold 相似度阈值（汉明距离）
     * @param pageable 分页参数
     * @return 相似图片列表
     */
    public List<FileContent> findSimilarImages(String perceptualHash, int threshold, Pageable pageable) {
        // 先查找相同 perceptual_hash 的记录
        Page<FileContent> exactMatches = fileContentRepository.findByPerceptualHash(perceptualHash, pageable);

        // 如果要求更宽松的相似度，可以扩展搜索范围
        // 这里简化处理，只返回精确哈希匹配的结果
        // 更复杂的相似度搜索可以在应用层计算汉明距离筛选
        return exactMatches.getContent();
    }

    /**
     * 计算文件的完整哈希信息
     *
     * @param file 上传的文件
     * @return 哈希计算结果
     */
    public ImageHashResult computeHashes(MultipartFile file) throws IOException {
        return perceptualHashService.computeHashes(file);
    }

    /**
     * 创建新的文件内容记录
     *
     * @param hashResult 哈希计算结果
     * @param storageKey 存储键
     * @param url 访问 URL
     * @param firstUploaderId 首次上传者ID
     * @param originalFilename 原始文件名
     * @return 创建的文件内容记录
     */
    @Transactional
    public FileContent createFileContent(
            ImageHashResult hashResult,
            String storageKey,
            String url,
            UUID firstUploaderId,
            String originalFilename
    ) {
        FileContent fileContent = FileContent.builder()
                .sha256Hash(hashResult.getSha256Hash())
                .perceptualHash(hashResult.getPerceptualHash())
                .diffHash(hashResult.getDiffHash())
                .sizeBytes(hashResult.getSizeBytes())
                .contentType(hashResult.getContentType())
                .storageKey(storageKey)
                .url(url)
                .width(hashResult.getWidth())
                .height(hashResult.getHeight())
                .refCount(1)
                .originalFilename(originalFilename)
                .firstUploaderId(firstUploaderId)
                .build();

        return fileContentRepository.save(fileContent);
    }

    /**
     * 增加文件引用计数
     *
     * @param fileContentId 文件内容ID
     */
    @Transactional
    public void incrementRefCount(UUID fileContentId) {
        fileContentRepository.incrementRefCount(fileContentId);
        log.debug("Incremented ref count for file content: {}", fileContentId);
    }

    /**
     * 减少文件引用计数
     * 当引用计数归零时，应该删除实际存储的文件
     *
     * @param fileContentId 文件内容ID
     * @return 减少后的引用计数
     */
    @Transactional
    public int decrementRefCount(UUID fileContentId) {
        fileContentRepository.decrementRefCount(fileContentId);

        Optional<FileContent> fileContent = fileContentRepository.findById(fileContentId);
        if (fileContent.isPresent()) {
            int newRefCount = fileContent.get().getRefCount();
            log.debug("Decremented ref count for file content: {}, new count: {}",
                    fileContentId, newRefCount);
            return newRefCount;
        }
        return 0;
    }

    /**
     * 获取文件内容详情
     */
    public Optional<FileContent> getFileContent(UUID fileContentId) {
        return fileContentRepository.findById(fileContentId);
    }

    /**
     * 查找可以被清理的孤儿文件（引用计数 <= 0）
     *
     * @param pageable 分页参数
     * @return 孤儿文件列表
     */
    public List<FileContent> findOrphanFiles(Pageable pageable) {
        return fileContentRepository.findByRefCountLessThanEqual(0, pageable).getContent();
    }

    /**
     * 删除文件内容记录
     * 注意：实际存储的文件需要单独清理
     *
     * @param fileContentId 文件内容ID
     */
    @Transactional
    public void deleteFileContent(UUID fileContentId) {
        fileContentRepository.deleteById(fileContentId);
        log.info("Deleted file content record: {}", fileContentId);
    }

    /**
     * 比较两个感知哈希的相似度
     *
     * @param hash1 第一个哈希
     * @param hash2 第二个哈希
     * @return 汉明距离（0-64），越小越相似
     */
    public int calculateSimilarity(String hash1, String hash2) {
        return perceptualHashService.hammingDistance(hash1, hash2);
    }

    /**
     * 判断两张图片是否相似
     *
     * @param hash1 第一张图片的哈希
     * @param hash2 第二张图片的哈希
     * @param threshold 相似度阈值
     * @return 是否相似
     */
    public boolean isSimilar(String hash1, String hash2, int threshold) {
        return perceptualHashService.isSimilar(hash1, hash2, threshold);
    }
}
