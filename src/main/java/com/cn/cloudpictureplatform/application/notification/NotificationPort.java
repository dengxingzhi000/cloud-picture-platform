package com.cn.cloudpictureplatform.application.notification;

import java.util.Collection;
import java.util.UUID;

public interface NotificationPort {

    void notifyUploadCompleted(String username, UUID pictureId, String pictureName);

    void notifyReviewDecision(String ownerUsername, UUID pictureId, String pictureName, boolean approved, String reason);

    void notifyAdminNewUpload(UUID pictureId, String pictureName, String uploaderUsername);

    void notifyTeamInvite(String inviteeUsername, UUID teamId, String teamName, String inviterUsername);

    void notifyTeamPictureUploaded(Collection<String> usernames, UUID pictureId, String pictureName, String uploaderUsername);

    void notifyTeamMemberJoined(String username, UUID teamId, String teamName);
}
