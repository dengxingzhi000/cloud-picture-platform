package com.cn.cloudpictureplatform.interfaces.notification;

import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.cn.cloudpictureplatform.application.notification.NotificationService;
import com.cn.cloudpictureplatform.common.web.ApiResponse;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.notification.NotificationRecord;
import com.cn.cloudpictureplatform.infrastructure.security.AppUserPrincipal;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @AuthenticationPrincipal AppUserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Page<NotificationRecord> result = notificationService.listByUser(principal.getId(), page, size);
        var items = result.getContent().stream().map(this::toResponse).toList();
        return ApiResponse.ok(new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal AppUserPrincipal principal) {
        return ApiResponse.ok(Map.of("count", notificationService.countUnread(principal.getId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal AppUserPrincipal principal) {
        notificationService.markRead(id, principal.getId());
        return ApiResponse.ok(null);
    }

    @PatchMapping("/read-all")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal AppUserPrincipal principal) {
        notificationService.markAllRead(principal.getId());
        return ApiResponse.ok(null);
    }

    private NotificationResponse toResponse(NotificationRecord record) {
        return new NotificationResponse(
                record.getId(), record.getKind(), record.getTitle(),
                record.getBody(), record.getTargetId(),
                record.isRead(), record.getCreatedAt()
        );
    }
}
