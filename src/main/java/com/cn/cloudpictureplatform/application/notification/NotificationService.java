package com.cn.cloudpictureplatform.application.notification;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.domain.notification.NotificationRecord;
import com.cn.cloudpictureplatform.infrastructure.persistence.NotificationRecordRepository;

@Service
public class NotificationService {

    private final NotificationRecordRepository repository;

    public NotificationService(NotificationRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public NotificationRecord save(UUID userId, String kind, String title, String body, UUID targetId) {
        NotificationRecord record = NotificationRecord.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .kind(kind)
                .title(title)
                .body(body)
                .targetId(targetId)
                .read(false)
                .createdAt(Instant.now())
                .build();
        return repository.save(record);
    }

    @Transactional(readOnly = true)
    public Page<NotificationRecord> listByUser(UUID userId, int page, int size) {
        return repository.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return repository.countByUserIdAndReadFalse(userId);
    }

    @Transactional
    public boolean markRead(UUID id, UUID userId) {
        return repository.markAsRead(id, userId) > 0;
    }

    @Transactional
    public void markAllRead(UUID userId) {
        repository.markAllAsReadByUserId(userId);
    }
}
