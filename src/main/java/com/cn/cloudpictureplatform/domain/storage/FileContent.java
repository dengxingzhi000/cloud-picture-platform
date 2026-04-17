package com.cn.cloudpictureplatform.domain.storage;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 文件内容实体 - 存储去重后的实际文件元数据
 * 多个 PictureAsset 可以引用同一个 FileContent，实现存储去重
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(
        name = "file_content",
        indexes = {
                @Index(name = "idx_file_sha256", columnList = "sha256_hash"),
                @Index(name = "idx_file_phash", columnList = "perceptual_hash"),
                @Index(name = "idx_file_size", columnList = "size_bytes")
        }
)
public class FileContent extends BaseEntity {

    /**
     * SHA-256 内容哈希 - 用于精确去重（完全相同的文件）
     */
    @Column(name = "sha256_hash", nullable = false, length = 64)
    private String sha256Hash;

    /**
     * 感知哈希 (pHash) - 用于相似图片检测
     * 64位哈希值的十六进制表示
     */
    @Column(name = "perceptual_hash", length = 16)
    private String perceptualHash;

    /**
     * 差异哈希 (dHash) - 用于相似图片检测的备用方案
     * 64位哈希值的十六进制表示
     */
    @Column(name = "diff_hash", length = 16)
    private String diffHash;

    /**
     * 文件大小（字节）
     */
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /**
     * 文件 MIME 类型
     */
    @Column(name = "content_type", length = 120)
    private String contentType;

    /**
     * 存储服务商的键（如 COS key 或本地路径）
     */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /**
     * 可访问的 URL
     */
    @Column(length = 500)
    private String url;

    /**
     * 引用计数 - 有多少个 PictureAsset 引用此文件
     * 当引用计数归零时，可以安全删除实际存储的文件
     */
    @Column(name = "ref_count", nullable = false)
    @Builder.Default
    private int refCount = 1;

    /**
     * 图片宽度（如果是图片）
     */
    @Column
    private Integer width;

    /**
     * 图片高度（如果是图片）
     */
    @Column
    private Integer height;

    /**
     * 原始文件名（首次上传时的名称）
     */
    @Column(name = "original_filename", length = 200)
    private String originalFilename;

    /**
     * 首次上传者 ID
     */
    @Column(name = "first_uploader_id", columnDefinition = "uuid")
    private UUID firstUploaderId;

    /**
     * 增加引用计数
     */
    public void incrementRefCount() {
        this.refCount++;
    }

    /**
     * 减少引用计数
     * @return 减少后的引用计数
     */
    public int decrementRefCount() {
        if (this.refCount > 0) {
            this.refCount--;
        }
        return this.refCount;
    }

    /**
     * 是否可以安全删除（引用计数为 0）
     */
    public boolean isSafeToDelete() {
        return this.refCount <= 0;
    }
}
