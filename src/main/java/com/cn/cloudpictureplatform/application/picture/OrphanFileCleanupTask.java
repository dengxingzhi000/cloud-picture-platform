package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.domain.storage.FileContent;
import com.cn.cloudpictureplatform.domain.storage.FileDeduplicationService;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 孤儿文件清理任务
 * 定期清理引用计数为0的文件（存储空间回收）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanFileCleanupTask {

    private final FileDeduplicationService fileDeduplicationService;
    private final StorageService storageService;

    /**
     * 每天凌晨2点执行清理任务
     * 清理引用计数 <= 0 的孤儿文件
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void cleanupOrphanFiles() {
        log.info("Starting orphan file cleanup task");

        int cleanedCount = 0;
        int batchSize = 100;
        boolean hasMore = true;

        while (hasMore) {
            // 查找孤儿文件
            List<FileContent> orphans = fileDeduplicationService.findOrphanFiles(
                    PageRequest.of(0, batchSize)
            );

            if (orphans.isEmpty()) {
                hasMore = false;
                break;
            }

            for (FileContent orphan : orphans) {
                try {
                    // 删除实际存储的文件
                    deleteStorageFile(orphan.getStorageKey());

                    // 删除数据库记录
                    fileDeduplicationService.deleteFileContent(orphan.getId());

                    cleanedCount++;
                    log.info("Cleaned orphan file: fileContentId={}, storageKey={}",
                            orphan.getId(), orphan.getStorageKey());
                } catch (Exception e) {
                    log.error("Failed to clean orphan file: fileContentId={}", orphan.getId(), e);
                }
            }

            // 如果本批处理数量小于批次大小，说明没有更多数据了
            hasMore = orphans.size() >= batchSize;
        }

        log.info("Orphan file cleanup completed. Total cleaned: {}", cleanedCount);
    }

    /**
     * 删除存储服务商的实际文件
     */
    private void deleteStorageFile(String storageKey) {
        boolean deleted = storageService.delete(storageKey);
        if (deleted) {
            log.debug("Deleted storage file: {}", storageKey);
        } else {
            log.warn("Failed to delete storage file: {}", storageKey);
        }
    }
}
