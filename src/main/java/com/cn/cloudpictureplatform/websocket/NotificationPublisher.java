package com.cn.cloudpictureplatform.websocket;

import java.time.Instant;
import java.util.Collection;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.websocket.dto.CollabMessage;
import com.cn.cloudpictureplatform.websocket.dto.NotificationMessage;

@Service
public class NotificationPublisher {
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public NotificationPublisher(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void notifyUploadCompleted(String username, UUID pictureId, String pictureName) {
        NotificationMessage notification = NotificationMessage.builder()
                .kind(NotificationMessage.NotificationKind.UPLOAD_COMPLETE)
                .title("Picture upload completed")
                .body("Your picture \"" + pictureName + "\" is available now.")
                .targetId(pictureId)
                .timestamp(Instant.now())
                .build();
        sendToUser(username, notification);
    }

    public void notifyReviewDecision(
            String ownerUsername,
            UUID pictureId,
            String pictureName,
            boolean approved,
            String reason
    ) {
        String title = approved ? "Picture approved" : "Picture rejected";
        String body = approved
                ? "Your picture \"" + pictureName + "\" passed review."
                : "Your picture \"" + pictureName + "\" was rejected."
                + (reason == null || reason.isBlank() ? "" : " Reason: " + reason.trim());

        NotificationMessage notification = NotificationMessage.builder()
                .kind(approved ? NotificationMessage.NotificationKind.PICTURE_APPROVED
                        : NotificationMessage.NotificationKind.PICTURE_REJECTED)
                .title(title)
                .body(body)
                .targetId(pictureId)
                .timestamp(Instant.now())
                .build();

        sendToUser(ownerUsername, notification);

        JsonNode payloadNode = objectMapper.valueToTree(notification);
        CollabMessage reviewEvent = CollabMessage.builder()
                .type(CollabMessage.EventType.REVIEW_DECISION)
                .schemaVersion(PictureCollabController.EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .timestamp(Instant.now())
                .payload(payloadNode)
                .build();
        messagingTemplate.convertAndSend("/topic/pictures/" + pictureId + "/collab", reviewEvent);
    }

    public void notifyAdminNewUpload(UUID pictureId, String pictureName, String uploaderUsername) {
        NotificationMessage notification = NotificationMessage.builder()
                .kind(NotificationMessage.NotificationKind.REVIEW_PENDING)
                .title("New picture pending review")
                .body("Picture \"" + pictureName + "\" uploaded by " + uploaderUsername + " is waiting for review.")
                .targetId(pictureId)
                .timestamp(Instant.now())
                .build();

        messagingTemplate.convertAndSend("/topic/admin/notifications", notification);

        JsonNode payloadNode = objectMapper.valueToTree(notification);
        CollabMessage event = CollabMessage.builder()
                .type(CollabMessage.EventType.PICTURE_UPLOADED)
                .schemaVersion(PictureCollabController.EVENT_SCHEMA_VERSION)
                .pictureId(pictureId)
                .username(uploaderUsername)
                .timestamp(Instant.now())
                .payload(payloadNode)
                .build();
        messagingTemplate.convertAndSend("/topic/admin/reviews", event);
    }

    public void notifyTeamInvite(
            String inviteeUsername,
            UUID teamId,
            String teamName,
            String inviterUsername
    ) {
        NotificationMessage notification = NotificationMessage.builder()
                .kind(NotificationMessage.NotificationKind.TEAM_INVITE)
                .title("Team invitation")
                .body(inviterUsername + " invited you to join team \"" + teamName + "\".")
                .targetId(teamId)
                .timestamp(Instant.now())
                .build();
        sendToUser(inviteeUsername, notification);
    }

    public void notifyTeamPictureUploaded(
            Collection<String> usernames,
            UUID pictureId,
            String pictureName,
            String uploaderUsername
    ) {
        if (usernames == null || usernames.isEmpty()) {
            return;
        }
        NotificationMessage notification = NotificationMessage.builder()
                .kind(NotificationMessage.NotificationKind.TEAM_PICTURE_UPLOADED)
                .title("New team picture")
                .body(uploaderUsername + " uploaded \"" + pictureName + "\" to the team space.")
                .targetId(pictureId)
                .timestamp(Instant.now())
                .build();
        for (String username : usernames) {
            sendToUser(username, notification);
        }
    }

    private void sendToUser(String username, NotificationMessage notification) {
        if (username == null || username.isBlank()) {
            return;
        }
        messagingTemplate.convertAndSendToUser(username, "/queue/notifications", notification);
    }
}
