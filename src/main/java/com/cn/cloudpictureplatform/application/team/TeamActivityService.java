package com.cn.cloudpictureplatform.application.team;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.team.TeamActivity;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamActivityRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
@Transactional(readOnly = true)
public class TeamActivityService {
    private final TeamActivityRepository teamActivityRepository;
    private final AppUserRepository appUserRepository;
    private final ObjectMapper objectMapper;

    public TeamActivityService(
            TeamActivityRepository teamActivityRepository,
            AppUserRepository appUserRepository,
            ObjectMapper objectMapper
    ) {
        this.teamActivityRepository = teamActivityRepository;
        this.appUserRepository = appUserRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordActivity(UUID teamId, UUID actorId, String activityType, UUID targetId, Object payload) {
        String payloadJson = null;
        if (payload != null) {
            try {
                payloadJson = objectMapper.writeValueAsString(payload);
            } catch (JsonProcessingException ignored) {}
        }
        teamActivityRepository.save(TeamActivity.builder()
                .teamId(teamId).actorId(actorId)
                .activityType(activityType).targetId(targetId)
                .payload(payloadJson).build());
    }

    public PageResponse<TeamActivityResponse> listActivities(UUID teamId, int page, int size) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.clamp(size, 1, 100);
        var pageable = PageRequest.of(pageIndex, pageSize);
        var result = teamActivityRepository.findByTeamIdOrderByCreatedAtDesc(teamId, pageable);
        var actorIds = result.getContent().stream()
                .map(TeamActivity::getActorId).distinct().toList();
        Map<UUID, AppUser> userMap = appUserRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
        var items = result.getContent().stream()
                .map(a -> new TeamActivityResponse(
                        a.getId(), a.getTeamId(), a.getActorId(),
                        userMap.containsKey(a.getActorId()) ? userMap.get(a.getActorId()).getUsername() : null,
                        userMap.containsKey(a.getActorId()) ? userMap.get(a.getActorId()).getDisplayName() : null,
                        a.getActivityType(), a.getTargetId(), a.getPayload(), a.getCreatedAt()))
                .toList();
        return new PageResponse<>(items, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    public record TeamActivityResponse(
            UUID id, UUID teamId, UUID actorId,
            String actorUsername, String actorDisplayName,
            String activityType, UUID targetId, String payload,
            java.time.Instant createdAt
    ) {}
}
