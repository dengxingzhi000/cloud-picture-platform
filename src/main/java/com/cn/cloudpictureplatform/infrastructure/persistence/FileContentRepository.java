package com.cn.cloudpictureplatform.infrastructure.persistence;

import java.util.Optional;
import java.util.UUID;

import com.cn.cloudpictureplatform.domain.storage.FileContent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * 文件内容存储库 - 管理去重后的文件元数据
 */
@Repository
public interface FileContentRepository extends JpaRepository<FileContent, UUID> {

    /**
     * 根据 SHA-256 哈希查找文件内容
     * 用于精确去重检测
     */
    Optional<FileContent> findBySha256Hash(String sha256Hash);

    /**
     * 根据感知哈希查找相似的图片
     * 用于相似图片检测
     */
    Page<FileContent> findByPerceptualHash(String perceptualHash, Pageable pageable);

    /**
     * 根据差异哈希查找相似的图片
     */
    Page<FileContent> findByDiffHash(String diffHash, Pageable pageable);

    /**
     * 根据文件大小范围查找（辅助去重检测）
     */
    Page<FileContent> findBySizeBytesBetween(long minSize, long maxSize, Pageable pageable);

    /**
     * 查找引用计数小于等于指定值的文件（用于清理孤儿文件）
     */
    Page<FileContent> findByRefCountLessThanEqual(int maxRefCount, Pageable pageable);

    /**
     * 原子性增加引用计数
     */
    @Modifying
    @Query("UPDATE FileContent fc SET fc.refCount = fc.refCount + 1 WHERE fc.id = :id")
    void incrementRefCount(@Param("id") UUID id);

    /**
     * 原子性减少引用计数
     */
    @Modifying
    @Query("UPDATE FileContent fc SET fc.refCount = fc.refCount - 1 WHERE fc.id = :id")
    void decrementRefCount(@Param("id") UUID id);

    /**
     * 检查是否存在指定 SHA-256 哈希的文件
     */
    boolean existsBySha256Hash(String sha256Hash);

    /**
     * 根据存储键查找文件
     */
    Optional<FileContent> findByStorageKey(String storageKey);
}
