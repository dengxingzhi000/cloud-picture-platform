package com.cn.cloudpictureplatform.application.team;

import com.cn.cloudpictureplatform.application.team.dto.TeamCreateRequest;
import com.cn.cloudpictureplatform.application.team.dto.TeamInviteRequest;
import com.cn.cloudpictureplatform.application.team.dto.TeamMemberResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamResponse;
import com.cn.cloudpictureplatform.application.team.dto.TeamRoleUpdateRequest;
import com.cn.cloudpictureplatform.application.team.dto.TeamUpdateRequest;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
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
import com.cn.cloudpictureplatform.domain.events.DomainEventBus;
import com.cn.cloudpictureplatform.domain.events.TeamInviteEvent;
import com.cn.cloudpictureplatform.domain.events.TeamMemberJoinedEvent;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional
public class TeamCommandService {
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final TeamMemberEventRepository teamMemberEventRepository;
    private final SpaceRepository spaceRepository;
    private final AppUserRepository appUserRepository;
    private final DomainEventBus domainEventBus;

    public TeamCommandService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            TeamMemberEventRepository teamMemberEventRepository,
            SpaceRepository spaceRepository,
            AppUserRepository appUserRepository,
            DomainEventBus domainEventBus
    ) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.teamMemberEventRepository = teamMemberEventRepository;
        this.spaceRepository = spaceRepository;
        this.appUserRepository = appUserRepository;
        this.domainEventBus = domainEventBus;
    }

    public TeamResponse createTeam(UUID ownerId, TeamCreateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName()))
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "team name is required");
        String name = request.getName().trim();
        String description = StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null;

        Team team = Team.builder().ownerId(ownerId).name(name).description(description).build();
        Team saved = teamRepository.save(team);

        Space space = Space.builder().ownerId(ownerId).teamId(saved.getId()).type(SpaceType.TEAM).name(name).build();
        Space savedSpace = spaceRepository.save(space);

        TeamMember ownerMember = TeamMember.builder()
                .teamId(saved.getId()).userId(ownerId).role(TeamRole.OWNER)
                .status(TeamMemberStatus.ACTIVE).joinedAt(Instant.now()).build();
        TeamMember savedMember = teamMemberRepository.save(ownerMember);
        recordEvent(saved.getId(), ownerId, ownerId, TeamMemberEventType.JOINED, savedMember.getRole());

        return TeamResponse.builder()
                .id(saved.getId()).name(saved.getName()).description(saved.getDescription())
                .ownerId(saved.getOwnerId()).spaceId(savedSpace.getId())
                .createdAt(saved.getCreatedAt()).build();
    }

    public TeamResponse updateTeam(UUID teamId, UUID requesterId, TeamUpdateRequest request) {
        if (request == null || !StringUtils.hasText(request.getName()))
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "team name is required");
        TeamMember member = requireActiveMember(teamId, requesterId);
        requireAdmin(member);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "team not found"));
        String name = request.getName().trim();
        String description = StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : null;
        String previousName = team.getName();
        String previousDescription = team.getDescription();
        team.setName(name);
        team.setDescription(description);
        Team saved = teamRepository.save(team);

        UUID spaceId = spaceRepository.findByTeamId(teamId)
                .map(space -> { space.setName(name); return spaceRepository.save(space); })
                .map(Space::getId).orElse(null);

        recordEvent(teamId, requesterId, requesterId, TeamMemberEventType.TEAM_UPDATED, member.getRole(),
                buildTeamUpdateDetail(previousName, previousDescription, name, description));
        return TeamResponse.builder()
                .id(saved.getId()).name(saved.getName()).description(saved.getDescription())
                .ownerId(saved.getOwnerId()).spaceId(spaceId)
                .createdAt(saved.getCreatedAt()).build();
    }

    public TeamMemberResponse inviteMember(UUID teamId, UUID inviterId, TeamInviteRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()))
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "username is required");
        TeamMember inviter = requireActiveMember(teamId, inviterId);
        requireAdmin(inviter);

        AppUser invitee = findUserByUsernameOrEmail(request.getUsername().trim());
        if (invitee == null) throw new ApiException(ApiErrorCode.NOT_FOUND, "user not found");
        TeamMember existing = teamMemberRepository.findByTeamIdAndUserId(teamId, invitee.getId()).orElse(null);
        if (existing != null) throw new ApiException(ApiErrorCode.BAD_REQUEST, "user already invited or a member");

        TeamRole role = request.getRole() == null ? TeamRole.MEMBER : request.getRole();
        if (role == TeamRole.OWNER) throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid role");
        TeamMember member = TeamMember.builder()
                .teamId(teamId).userId(invitee.getId()).role(role)
                .status(TeamMemberStatus.INVITED).invitedBy(inviterId).invitedAt(Instant.now()).build();
        TeamMember savedMember = teamMemberRepository.save(member);
        recordEvent(teamId, invitee.getId(), inviterId, TeamMemberEventType.INVITED, role);
        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "team not found"));
        AppUser inviterUser = appUserRepository.findById(inviterId).orElse(null);
        domainEventBus.publish(new TeamInviteEvent(
                teamId, team.getName(),
                invitee.getUsername(),
                inviterUser == null ? "unknown" : inviterUser.getUsername()));
        return toMemberResponse(savedMember, invitee, inviterUser);
    }

    public TeamMemberResponse acceptInvite(UUID teamId, UUID userId) {
        TeamMember member = requirePendingInvite(teamId, userId);
        member.setStatus(TeamMemberStatus.ACTIVE);
        member.setJoinedAt(Instant.now());
        TeamMember saved = teamMemberRepository.save(member);
        recordEvent(teamId, userId, userId, TeamMemberEventType.JOINED, saved.getRole());
        AppUser user = appUserRepository.findById(userId).orElse(null);
        AppUser inviter = member.getInvitedBy() != null ? appUserRepository.findById(member.getInvitedBy()).orElse(null) : null;
        Team team = teamRepository.findById(teamId).orElse(null);
        if (team != null && user != null) {
            domainEventBus.publish(new TeamMemberJoinedEvent(
                    teamId, team.getName(),
                    user.getId(), user.getUsername()));
        }
        return toMemberResponse(saved, user, inviter);
    }

    public void rejectInvite(UUID teamId, UUID userId) {
        TeamMember member = requirePendingInvite(teamId, userId);
        recordEvent(teamId, userId, userId, TeamMemberEventType.INVITE_REJECTED, member.getRole());
        teamMemberRepository.delete(member);
    }

    public TeamMemberResponse cancelInvite(UUID teamId, UUID actorId, UUID targetUserId) {
        TeamMember actor = requireActiveMember(teamId, actorId);
        requireAdmin(actor);
        TeamMember member = requirePendingInvite(teamId, targetUserId);
        AppUser user = appUserRepository.findById(targetUserId).orElse(null);
        AppUser inviter = member.getInvitedBy() != null ? appUserRepository.findById(member.getInvitedBy()).orElse(null) : null;
        recordEvent(teamId, targetUserId, actorId, TeamMemberEventType.INVITE_CANCELED, member.getRole());
        teamMemberRepository.delete(member);
        return toMemberResponse(member, user, inviter);
    }

    public TeamMemberResponse updateRole(UUID teamId, UUID actorId, UUID targetUserId, TeamRoleUpdateRequest request) {
        if (request == null || request.getRole() == null)
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "role is required");
        TeamMember actor = requireActiveMember(teamId, actorId);
        requireOwner(actor);
        TeamMember target = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "member not found"));
        if (target.getRole() == TeamRole.OWNER)
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "owner role cannot be changed");
        if (request.getRole() == TeamRole.OWNER)
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid role");
        target.setRole(request.getRole());
        TeamMember saved = teamMemberRepository.save(target);
        AppUser user = appUserRepository.findById(targetUserId).orElse(null);
        AppUser inviter = saved.getInvitedBy() != null ? appUserRepository.findById(saved.getInvitedBy()).orElse(null) : null;
        return toMemberResponse(saved, user, inviter);
    }

    public void removeMember(UUID teamId, UUID actorId, UUID targetUserId) {
        TeamMember actor = requireActiveMember(teamId, actorId);
        TeamMember target = teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "member not found"));
        if (target.getRole() == TeamRole.OWNER)
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "owner cannot be removed");
        if (actorId.equals(targetUserId)) {
            recordEvent(teamId, targetUserId, actorId, TeamMemberEventType.LEFT, target.getRole());
            teamMemberRepository.delete(target);
            return;
        }
        if (actor.getRole() == TeamRole.ADMIN) {
            if (target.getRole() != TeamRole.MEMBER)
                throw new ApiException(ApiErrorCode.FORBIDDEN, "insufficient permissions");
        } else if (actor.getRole() != TeamRole.OWNER) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "insufficient permissions");
        }
        recordEvent(teamId, targetUserId, actorId, TeamMemberEventType.MEMBER_REMOVED, target.getRole());
        teamMemberRepository.delete(target);
    }

    // ---- helpers ----

    private TeamMember requireActiveMember(UUID teamId, UUID userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "team not found"));
        if (member.getStatus() != TeamMemberStatus.ACTIVE)
            throw new ApiException(ApiErrorCode.FORBIDDEN, "membership is not active");
        return member;
    }

    private TeamMember requirePendingInvite(UUID teamId, UUID userId) {
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(teamId, userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "invite not found"));
        if (member.getStatus() != TeamMemberStatus.INVITED)
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invite is not pending");
        return member;
    }

    private void requireAdmin(TeamMember member) {
        if (member.getRole() != TeamRole.OWNER && member.getRole() != TeamRole.ADMIN)
            throw new ApiException(ApiErrorCode.FORBIDDEN, "insufficient permissions");
    }

    private void requireOwner(TeamMember member) {
        if (member.getRole() != TeamRole.OWNER)
            throw new ApiException(ApiErrorCode.FORBIDDEN, "owner role required");
    }

    private AppUser findUserByUsernameOrEmail(String value) {
        if (!StringUtils.hasText(value)) return null;
        return appUserRepository.findByUsername(value)
                .or(() -> appUserRepository.findByEmail(value))
                .orElse(null);
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

    private void recordEvent(UUID teamId, UUID userId, UUID actorId, TeamMemberEventType type, TeamRole role) {
        recordEvent(teamId, userId, actorId, type, role, null);
    }

    private void recordEvent(UUID teamId, UUID userId, UUID actorId, TeamMemberEventType type, TeamRole role, String detail) {
        teamMemberEventRepository.save(TeamMemberEvent.builder()
                .teamId(teamId).userId(userId).actorId(actorId).type(type).role(role).detail(detail).build());
    }

    private String buildTeamUpdateDetail(String previousName, String previousDescription, String nextName, String nextDescription) {
        String beforeName = previousName == null ? "" : previousName.replace("\"", "\\\"");
        String afterName = nextName == null ? "" : nextName.replace("\"", "\\\"");
        String beforeDesc = previousDescription == null ? "" : previousDescription.replace("\"", "\\\"");
        String afterDesc = nextDescription == null ? "" : nextDescription.replace("\"", "\\\"");
        return "{\"name\":{\"from\":\"" + beforeName + "\",\"to\":\"" + afterName
                + "\"},\"description\":{\"from\":\"" + beforeDesc + "\",\"to\":\"" + afterDesc + "\"}}";
    }
}
