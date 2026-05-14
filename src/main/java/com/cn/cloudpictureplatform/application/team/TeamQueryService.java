package com.cn.cloudpictureplatform.application.team;

import com.cn.cloudpictureplatform.application.team.dto.TeamInviteSummaryResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamMemberEventResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamMemberResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamSummaryResponse;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.common.web.PageResponse;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.team.Team;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEvent;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEventType;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberEventRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class TeamQueryService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMemberEventRepository teamMemberEventRepository;
    private final SpaceRepository spaceRepository;
    private final AppUserRepository appUserRepository;

    public TeamQueryService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamMemberEventRepository teamMemberEventRepository,
            SpaceRepository spaceRepository,
            AppUserRepository appUserRepository
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamMemberEventRepository = teamMemberEventRepository;
        this.spaceRepository = spaceRepository;
        this.appUserRepository = appUserRepository;
    }

    public List<TeamSummaryResponse> listMyTeams(UUID userId) {
        List<TeamMember> memberships = teamMemberRepository
                .findByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE);
        if (memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = memberships.stream().map(TeamMember::getTeamId).toList();
        Map<UUID, Team> teamMap = buildTeamMap(teamIds);
        Map<UUID, UUID> teamSpaceMap = spaceRepository.findByTeamIdIn(teamIds).stream()
                .collect(Collectors.toMap(Space::getTeamId, Space::getId));
        Map<UUID, Long> memberCountMap = teamMemberRepository
                .countByTeamIdInAndStatus(teamIds, TeamMemberStatus.ACTIVE).stream()
                .collect(Collectors.toMap(
                        TeamMemberRepository.TeamMemberCount::getTeamId,
                        TeamMemberRepository.TeamMemberCount::getCnt
                ));
        List<TeamSummaryResponse> responses = new ArrayList<>();
        for (TeamMember membership : memberships) {
            Team team = teamMap.get(membership.getTeamId());
            if (team == null) continue;
            long memberCount = memberCountMap.getOrDefault(membership.getTeamId(), 0L);
            responses.add(TeamSummaryResponse.builder()
                    .id(team.getId()).name(team.getName()).ownerId(team.getOwnerId())
                    .spaceId(teamSpaceMap.get(team.getId()))
                    .role(membership.getRole()).memberCount(memberCount)
                    .createdAt(team.getCreatedAt()).build());
        }
        return responses;
    }

    public TeamResponse getTeamDetail(UUID teamId, UUID requesterId) {
        requireMember(teamId, requesterId);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "team not found"));
        UUID spaceId = spaceRepository.findByTeamId(teamId)
                .map(Space::getId).orElse(null);
        return TeamResponse.builder()
                .id(team.getId()).name(team.getName()).description(team.getDescription())
                .ownerId(team.getOwnerId()).spaceId(spaceId).createdAt(team.getCreatedAt()).build();
    }

    public List<TeamMemberResponse> listMembers(UUID teamId, UUID requesterId) {
        requireActiveMember(teamId, requesterId);
        List<TeamMember> members = teamMemberRepository
                .findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE);
        return toMemberResponses(members);
    }

    public List<TeamMemberResponse> listTeamInvites(UUID teamId, UUID requesterId) {
        requireAdminMember(teamId, requesterId);
        List<TeamMember> invites = teamMemberRepository
                .findByTeamIdAndStatus(teamId, TeamMemberStatus.INVITED);
        return toMemberResponses(invites);
    }

    public PageResponse<TeamMemberResponse> listInviteHistory(
            UUID teamId, UUID requesterId, int page, int size,
            TeamMemberStatus status, TeamRole role,
            Instant invitedAfter, Instant invitedBefore,
            Instant joinedAfter, Instant joinedBefore,
            String sortBy, String sortDir
    ) {
        requireAdminMember(teamId, requesterId);
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), 100);
        Sort sort = resolveHistorySort(sortBy, sortDir);
        var pageable = PageRequest.of(pageIndex, pageSize, sort);
        Specification<TeamMember> spec = buildInviteHistorySpec(
                teamId, status, role, invitedAfter, invitedBefore, joinedAfter, joinedBefore);
        var result = teamMemberRepository.findAll(spec, pageable);
        return buildPageResponse(result, this::toMemberResponses);
    }

    public List<TeamInviteSummaryResponse> listMyInvites(UUID userId) {
        List<TeamMember> invites = teamMemberRepository
                .findByUserIdAndStatus(userId, TeamMemberStatus.INVITED);
        if (invites.isEmpty()) return List.of();
        List<UUID> teamIds = invites.stream().map(TeamMember::getTeamId).toList();
        Map<UUID, Team> teamMap = buildTeamMap(teamIds);
        Map<UUID, AppUser> inviterMap = buildInviterMap(invites);
        return invites.stream().map(invite -> {
            Team team = teamMap.get(invite.getTeamId());
            AppUser inviter = inviterMap.get(invite.getInvitedBy());
            return TeamInviteSummaryResponse.builder()
                    .teamId(invite.getTeamId()).teamName(team == null ? null : team.getName())
                    .role(invite.getRole()).invitedAt(invite.getInvitedAt())
                    .invitedBy(invite.getInvitedBy())
                    .inviterUsername(inviter == null ? null : inviter.getUsername())
                    .inviterDisplayName(inviter == null ? null : inviter.getDisplayName())
                    .inviterEmail(inviter == null ? null : inviter.getEmail())
                    .inviterAvatarUrl(inviter == null ? null : inviter.getAvatarUrl())
                    .build();
        }).toList();
    }

    public PageResponse<TeamMemberEventResponse> listMemberEvents(
            UUID teamId, UUID requesterId, int page, int size,
            TeamMemberEventType type, UUID userId, UUID actorId,
            Instant createdAfter, Instant createdBefore,
            String sortBy, String sortDir
    ) {
        requireAdminMember(teamId, requesterId);
        var result = findMemberEventPage(teamId, page, size, 100,
                type, userId, actorId, createdAfter, createdBefore, sortBy, sortDir);
        return buildPageResponse(result, this::toEventResponses);
    }

    public PageResponse<TeamMemberEventResponse> listInviteCancelEvents(
            UUID teamId, UUID requesterId, int page, int size,
            UUID userId, UUID actorId,
            Instant createdAfter, Instant createdBefore,
            String sortBy, String sortDir
    ) {
        return listMemberEvents(teamId, requesterId, page, size,
                TeamMemberEventType.INVITE_CANCELED, userId, actorId,
                createdAfter, createdBefore, sortBy, sortDir);
    }

    public List<TeamMemberEventResponse> exportMemberEvents(
            UUID teamId, UUID requesterId,
            TeamMemberEventType type, UUID userId, UUID actorId,
            Instant createdAfter, Instant createdBefore,
            String sortBy, String sortDir, int limit
    ) {
        requireAdminMember(teamId, requesterId);
        int pageSize = Math.min(Math.max(1, limit), 10000);
        var result = findMemberEventPage(teamId, 0, pageSize, 10000,
                type, userId, actorId, createdAfter, createdBefore, sortBy, sortDir);
        return toEventResponses(result.getContent());
    }

    // ---- helper: permission checks ----

    TeamMember requireActiveMember(UUID teamId, UUID userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "team not found"));
        if (member.getStatus() != TeamMemberStatus.ACTIVE)
            throw new ApiException(ApiErrorCode.FORBIDDEN, "membership is not active");
        return member;
    }

    TeamMember requireAdminMember(UUID teamId, UUID requesterId) {
        TeamMember member = requireActiveMember(teamId, requesterId);
        requireAdmin(member);
        return member;
    }

    private TeamMember requireMember(UUID teamId, UUID userId) {
        return teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "team not found"));
    }

    private void requireAdmin(TeamMember member) {
        if (member.getRole() != TeamRole.OWNER && member.getRole() != TeamRole.ADMIN)
            throw new ApiException(ApiErrorCode.FORBIDDEN, "insufficient permissions");
    }

    // ---- helper: event page ----

    private org.springframework.data.domain.Page<TeamMemberEvent> findMemberEventPage(
            UUID teamId, int page, int size, int maxSize,
            TeamMemberEventType type, UUID userId, UUID actorId,
            Instant createdAfter, Instant createdBefore,
            String sortBy, String sortDir
    ) {
        int pageIndex = Math.max(0, page);
        int pageSize = Math.min(Math.max(1, size), maxSize);
        Sort sort = resolveEventSort(sortBy, sortDir);
        var pageable = PageRequest.of(pageIndex, pageSize, sort);
        Specification<TeamMemberEvent> spec = buildMemberEventSpec(
                teamId, type, userId, actorId, createdAfter, createdBefore);
        return teamMemberEventRepository.findAll(spec, pageable);
    }

    // ---- helper: paginated response ----

    private <T, R> PageResponse<R> buildPageResponse(
            org.springframework.data.domain.Page<T> page,
            Function<List<T>, List<R>> mapper
    ) {
        List<R> items = mapper.apply(page.getContent());
        return new PageResponse<>(items, page.getTotalElements(), page.getNumber(), page.getSize());
    }

    // ---- helper: member→DTO ----

    private List<TeamMemberResponse> toMemberResponses(List<TeamMember> members) {
        if (members == null || members.isEmpty()) return List.of();
        Map<UUID, AppUser> userMap = buildUserMapFromMembers(members);
        Map<UUID, AppUser> inviterMap = buildInviterMap(members);
        return members.stream().map(member -> {
            AppUser user = userMap.get(member.getUserId());
            AppUser inviter = inviterMap.get(member.getInvitedBy());
            return toMemberResponse(member, user, inviter);
        }).toList();
    }

    private TeamMemberResponse toMemberResponse(TeamMember member, AppUser user, AppUser inviter) {
        return TeamMemberResponse.builder()
                .userId(member.getUserId())
                .username(user == null ? null : user.getUsername())
                .displayName(user == null ? null : user.getDisplayName())
                .invitedBy(member.getInvitedBy())
                .inviterUsername(inviter == null ? null : inviter.getUsername())
                .inviterDisplayName(inviter == null ? null : inviter.getDisplayName())
                .inviterEmail(inviter == null ? null : inviter.getEmail())
                .inviterAvatarUrl(inviter == null ? null : inviter.getAvatarUrl())
                .role(member.getRole()).status(member.getStatus())
                .invitedAt(member.getInvitedAt()).joinedAt(member.getJoinedAt())
                .build();
    }

    // ---- helper: event→DTO ----

    private List<TeamMemberEventResponse> toEventResponses(List<TeamMemberEvent> events) {
        if (events == null || events.isEmpty()) return List.of();
        List<UUID> userIds = events.stream().map(TeamMemberEvent::getUserId)
                .filter(Objects::nonNull).distinct().toList();
        List<UUID> actorIds = events.stream().map(TeamMemberEvent::getActorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, AppUser> userMap = buildUserMapByIds(userIds);
        Map<UUID, AppUser> actorMap = buildUserMapByIds(actorIds);
        return events.stream().map(event -> {
            AppUser user = userMap.get(event.getUserId());
            AppUser actor = actorMap.get(event.getActorId());
            return TeamMemberEventResponse.builder()
                    .id(event.getId()).teamId(event.getTeamId())
                    .userId(event.getUserId())
                    .username(user == null ? null : user.getUsername())
                    .displayName(user == null ? null : user.getDisplayName())
                    .actorId(event.getActorId())
                    .actorUsername(actor == null ? null : actor.getUsername())
                    .actorDisplayName(actor == null ? null : actor.getDisplayName())
                    .type(event.getType()).role(event.getRole()).detail(event.getDetail())
                    .createdAt(event.getCreatedAt()).build();
        }).toList();
    }

    // ---- helper: maps ----

    private Map<UUID, AppUser> buildUserMapFromMembers(List<TeamMember> members) {
        List<UUID> ids = members.stream().map(TeamMember::getUserId).distinct().toList();
        return buildUserMapByIds(ids);
    }

    private Map<UUID, AppUser> buildInviterMap(List<TeamMember> members) {
        List<UUID> ids = members.stream().map(TeamMember::getInvitedBy)
                .filter(Objects::nonNull).distinct().toList();
        return buildUserMapByIds(ids);
    }

    private Map<UUID, AppUser> buildUserMapByIds(List<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) return new HashMap<>();
        return appUserRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    private Map<UUID, Team> buildTeamMap(List<UUID> teamIds) {
        if (teamIds == null || teamIds.isEmpty()) return new HashMap<>();
        return teamRepository.findAllById(teamIds).stream()
                .collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    // ---- helper: sort ----

    private Sort resolveHistorySort(String sortBy, String sortDir) {
        return resolveSort(sortBy, sortDir, "invitedAt", Set.of("invitedAt", "joinedAt", "createdAt", "updatedAt"));
    }

    private Sort resolveEventSort(String sortBy, String sortDir) {
        return resolveSort(sortBy, sortDir, "createdAt", Set.of("createdAt", "updatedAt"));
    }

    private Sort resolveSort(String sortBy, String sortDir, String defaultField, Set<String> allowedFields) {
        String resolvedSortBy = StringUtils.hasText(sortBy) ? sortBy : defaultField;
        if (!allowedFields.contains(resolvedSortBy))
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort field");
        Sort.Direction direction = Sort.Direction.DESC;
        if (StringUtils.hasText(sortDir)) {
            if ("asc".equalsIgnoreCase(sortDir)) direction = Sort.Direction.ASC;
            else if (!"desc".equalsIgnoreCase(sortDir))
                throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid sort direction");
        }
        return Sort.by(direction, resolvedSortBy);
    }

    // ---- helper: specs ----

    private Specification<TeamMember> buildInviteHistorySpec(
            UUID teamId, TeamMemberStatus status, TeamRole role,
            Instant invitedAfter, Instant invitedBefore,
            Instant joinedAfter, Instant joinedBefore
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("teamId"), teamId));
            if (status != null) predicates.add(builder.equal(root.get("status"), status));
            if (role != null) predicates.add(builder.equal(root.get("role"), role));
            if (invitedAfter != null) predicates.add(builder.greaterThanOrEqualTo(root.get("invitedAt"), invitedAfter));
            if (invitedBefore != null) predicates.add(builder.lessThanOrEqualTo(root.get("invitedAt"), invitedBefore));
            if (joinedAfter != null) predicates.add(builder.greaterThanOrEqualTo(root.get("joinedAt"), joinedAfter));
            if (joinedBefore != null) predicates.add(builder.lessThanOrEqualTo(root.get("joinedAt"), joinedBefore));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Specification<TeamMemberEvent> buildMemberEventSpec(
            UUID teamId, TeamMemberEventType type, UUID userId, UUID actorId,
            Instant createdAfter, Instant createdBefore
    ) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("teamId"), teamId));
            if (type != null) predicates.add(builder.equal(root.get("type"), type));
            if (userId != null) predicates.add(builder.equal(root.get("userId"), userId));
            if (actorId != null) predicates.add(builder.equal(root.get("actorId"), actorId));
            if (createdAfter != null) predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), createdAfter));
            if (createdBefore != null) predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), createdBefore));
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }
}
