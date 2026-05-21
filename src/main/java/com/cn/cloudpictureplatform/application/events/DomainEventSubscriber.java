package com.cn.cloudpictureplatform.application.events;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;
import com.cn.cloudpictureplatform.application.notification.NotificationService;
import com.cn.cloudpictureplatform.application.outbox.OutboxService;
import com.cn.cloudpictureplatform.domain.events.PictureReviewedEvent;
import com.cn.cloudpictureplatform.domain.events.PictureUploadedEvent;
import com.cn.cloudpictureplatform.domain.events.TeamInviteEvent;
import com.cn.cloudpictureplatform.domain.events.TeamMemberJoinedEvent;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class DomainEventSubscriber {

    private final OutboxService outboxService;
    private final NotificationService notificationService;
    private final AppUserRepository appUserRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ObjectMapper objectMapper;

    public DomainEventSubscriber(
            OutboxService outboxService,
            NotificationService notificationService,
            AppUserRepository appUserRepository,
            TeamMemberRepository teamMemberRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxService = outboxService;
        this.notificationService = notificationService;
        this.appUserRepository = appUserRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureReviewed(PictureReviewedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        String payload = toJson("""
                {"ownerUsername":"%s","pictureName":"%s","approved":%s,"reason":"%s"}
                """.formatted(
                owner == null ? "" : owner.getUsername(),
                escape(event.pictureName()),
                event.approved(),
                escape(event.reason() != null ? event.reason() : "")
        ));
        if (payload != null) {
            outboxService.writeEvent("picture", event.pictureId(), "REVIEW_DECISION", payload);
        }
        if (owner != null) {
            notificationService.save(owner.getId(),
                    event.approved() ? "PICTURE_APPROVED" : "PICTURE_REJECTED",
                    event.approved() ? "Picture approved" : "Picture rejected",
                    "Your picture \"" + event.pictureName() + "\" was " + (event.approved() ? "approved." : "rejected."),
                    event.pictureId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPictureUploaded(PictureUploadedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        String ownerUsername = owner == null ? "" : owner.getUsername();

        String uploadPayload = toJson("""
                {"ownerUsername":"%s","pictureName":"%s"}
                """.formatted(ownerUsername, escape(event.pictureName())));
        if (uploadPayload != null) {
            outboxService.writeEvent("picture", event.pictureId(), "UPLOAD_COMPLETE", uploadPayload);
        }
        if (owner != null) {
            notificationService.save(owner.getId(), "UPLOAD_COMPLETE",
                    "Picture upload completed",
                    "Your picture \"" + event.pictureName() + "\" is available now.",
                    event.pictureId());
        }

        if (event.isPublic()) {
            String adminPayload = toJson("""
                    {"uploaderUsername":"%s","pictureName":"%s"}
                    """.formatted(ownerUsername, escape(event.pictureName())));
            if (adminPayload != null) {
                outboxService.writeEvent("picture", event.pictureId(), "ADMIN_NEW_UPLOAD", adminPayload);
            }
        }

        if (event.teamId() != null) {
            List<UUID> memberUserIds = teamMemberRepository
                    .findByTeamIdAndStatus(event.teamId(), TeamMemberStatus.ACTIVE)
                    .stream()
                    .map(TeamMember::getUserId)
                    .filter(userId -> !userId.equals(event.ownerId()))
                    .toList();

            if (!memberUserIds.isEmpty()) {
                Map<UUID, String> usernameMap = appUserRepository.findAllById(memberUserIds).stream()
                        .filter(user -> StringUtils.hasText(user.getUsername()))
                        .collect(Collectors.toMap(AppUser::getId, AppUser::getUsername));

                Collection<String> usernames = memberUserIds.stream()
                        .map(usernameMap::get)
                        .filter(Objects::nonNull)
                        .toList();

                if (!usernames.isEmpty()) {
                    String teamPayload = toJson("""
                            {"usernames":[""" + usernames.stream().map(u -> "\"" + u + "\"").collect(Collectors.joining(",")) + """
                            ],"pictureName":"%s","uploaderUsername":"%s"}
                            """.formatted(escape(event.pictureName()), ownerUsername));
                    if (teamPayload != null) {
                        outboxService.writeEvent("picture", event.pictureId(), "TEAM_UPLOAD", teamPayload);
                    }
                }
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamInvite(TeamInviteEvent event) {
        String payload = toJson("""
                {"teamName":"%s","inviterUsername":"%s","inviteeUsername":"%s"}
                """.formatted(escape(event.teamName()), escape(event.inviterUsername()), escape(event.inviteeUsername())));
        if (payload != null) {
            outboxService.writeEvent("team", event.teamId(), "TEAM_INVITE", payload);
        }
        AppUser invitee = appUserRepository.findByUsername(event.inviteeUsername()).orElse(null);
        if (invitee != null) {
            notificationService.save(invitee.getId(), "TEAM_INVITE",
                    "Team invitation",
                    event.inviterUsername() + " invited you to join team \"" + event.teamName() + "\".",
                    event.teamId());
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTeamMemberJoined(TeamMemberJoinedEvent event) {
        String payload = toJson("""
                {"teamName":"%s","username":"%s"}
                """.formatted(escape(event.teamName()), escape(event.username())));
        if (payload != null) {
            outboxService.writeEvent("team", event.teamId(), "TEAM_MEMBER_JOINED", payload);
        }
        List<TeamMember> members = teamMemberRepository.findByTeamIdAndStatus(event.teamId(), TeamMemberStatus.ACTIVE);
        for (TeamMember member : members) {
            if (!member.getUserId().equals(event.userId())) {
                notificationService.save(member.getUserId(), "TEAM_MEMBER_JOINED",
                        "New team member",
                        event.username() + " joined team \"" + event.teamName() + "\".",
                        event.teamId());
            }
        }
    }

    private String toJson(String raw) {
        try {
            return objectMapper.writeValueAsString(objectMapper.readTree(raw));
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}