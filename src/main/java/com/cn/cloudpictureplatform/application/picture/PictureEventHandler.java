package com.cn.cloudpictureplatform.application.picture;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.websocket.NotificationPublisher;

@Component
public class PictureEventHandler {

    private final NotificationPublisher notificationPublisher;
    private final AppUserRepository appUserRepository;
    private final TeamMemberRepository teamMemberRepository;

    public PictureEventHandler(
            NotificationPublisher notificationPublisher,
            AppUserRepository appUserRepository,
            TeamMemberRepository teamMemberRepository
    ) {
        this.notificationPublisher = notificationPublisher;
        this.appUserRepository = appUserRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePictureReviewed(PictureReviewedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        notificationPublisher.notifyReviewDecision(
                owner == null ? null : owner.getUsername(),
                event.pictureId(),
                event.pictureName(),
                event.approved(),
                event.reason()
        );
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePictureUploaded(PictureUploadedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        String ownerUsername = owner == null ? null : owner.getUsername();
        notificationPublisher.notifyUploadCompleted(ownerUsername, event.pictureId(), event.pictureName());

        if (event.isPublic()) {
            notificationPublisher.notifyAdminNewUpload(
                    event.pictureId(),
                    event.pictureName(),
                    ownerUsername == null ? "unknown" : ownerUsername
            );
        }

        if (event.teamId() != null) {
            Collection<String> usernames = teamMemberRepository
                    .findByTeamIdAndStatus(event.teamId(), TeamMemberStatus.ACTIVE)
                    .stream()
                    .map(TeamMember::getUserId)
                    .filter(userId -> !userId.equals(event.ownerId()))
                    .map(userId -> appUserRepository.findById(userId).orElse(null))
                    .filter(Objects::nonNull)
                    .map(AppUser::getUsername)
                    .filter(StringUtils::hasText)
                    .toList();
            notificationPublisher.notifyTeamPictureUploaded(
                    usernames,
                    event.pictureId(),
                    event.pictureName(),
                    ownerUsername == null ? "unknown" : ownerUsername
            );
        }
    }
}
