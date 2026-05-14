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
import com.cn.cloudpictureplatform.application.outbox.OutboxService;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class PictureEventHandler {

    private final OutboxService outboxService;
    private final AppUserRepository appUserRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final ObjectMapper objectMapper;

    public PictureEventHandler(
            OutboxService outboxService,
            AppUserRepository appUserRepository,
            TeamMemberRepository teamMemberRepository,
            ObjectMapper objectMapper
    ) {
        this.outboxService = outboxService;
        this.appUserRepository = appUserRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.objectMapper = objectMapper;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePictureReviewed(PictureReviewedEvent event) {
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
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePictureUploaded(PictureUploadedEvent event) {
        AppUser owner = appUserRepository.findById(event.ownerId()).orElse(null);
        String ownerUsername = owner == null ? "" : owner.getUsername();

        String uploadPayload = toJson("""
                {"ownerUsername":"%s","pictureName":"%s"}
                """.formatted(ownerUsername, escape(event.pictureName())));
        if (uploadPayload != null) {
            outboxService.writeEvent("picture", event.pictureId(), "UPLOAD_COMPLETE", uploadPayload);
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
            String teamPayload = toJson("""
                    {"usernames":[""" + usernames.stream().map(u -> "\"" + u + "\"").collect(Collectors.joining(",")) + """
                    ],"pictureName":"%s","uploaderUsername":"%s"}
                    """.formatted(escape(event.pictureName()), ownerUsername));
            if (teamPayload != null) {
                outboxService.writeEvent("picture", event.pictureId(), "TEAM_UPLOAD", teamPayload);
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
