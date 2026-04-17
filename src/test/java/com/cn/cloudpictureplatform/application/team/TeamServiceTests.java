package com.cn.cloudpictureplatform.application.team;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.team.Team;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberEvent;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberEventRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamRepository;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamInviteRequest;
import com.cn.cloudpictureplatform.interfaces.team.dto.TeamSummaryResponse;
import com.cn.cloudpictureplatform.websocket.NotificationPublisher;

@ExtendWith(MockitoExtension.class)
class TeamServiceTests {

    @Mock
    private TeamRepository teamRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamMemberEventRepository teamMemberEventRepository;
    @Mock
    private com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository spaceRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private NotificationPublisher notificationPublisher;

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(
                teamRepository,
                teamMemberRepository,
                teamMemberEventRepository,
                spaceRepository,
                appUserRepository,
                notificationPublisher
        );
    }

    @Test
    void shouldNotifyInviteeAfterTeamInviteCreated() {
        UUID teamId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        UUID inviteeId = UUID.randomUUID();

        TeamMember inviter = TeamMember.builder()
                .teamId(teamId)
                .userId(inviterId)
                .role(TeamRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .build();

        AppUser invitee = AppUser.builder()
                .username("bob")
                .email("bob@example.com")
                .passwordHash("hash")
                .displayName("Bob")
                .role(UserRole.USER)
                .build();
        invitee.setId(inviteeId);

        AppUser inviterUser = AppUser.builder()
                .username("alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .displayName("Alice")
                .role(UserRole.USER)
                .build();
        inviterUser.setId(inviterId);

        Team team = Team.builder()
                .ownerId(inviterId)
                .name("Design Team")
                .build();
        team.setId(teamId);

        when(teamMemberRepository.findByTeamIdAndUserId(teamId, inviterId)).thenReturn(Optional.of(inviter));
        when(appUserRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, inviteeId)).thenReturn(Optional.empty());
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(inviterId)).thenReturn(Optional.of(inviterUser));

        TeamInviteRequest request = new TeamInviteRequest();
        request.setUsername("bob");
        request.setRole(TeamRole.MEMBER);

        teamService.inviteMember(teamId, inviterId, request);

        verify(teamMemberEventRepository).save(any(TeamMemberEvent.class));
        verify(notificationPublisher).notifyTeamInvite("bob", teamId, "Design Team", "alice");
    }

    @Test
    void shouldReturnTeamSummaryWithSpaceId() {
        UUID userId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();

        TeamMember membership = TeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .role(TeamRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .build();

        Team team = Team.builder()
                .ownerId(userId)
                .name("Design Team")
                .build();
        team.setId(teamId);

        Space space = Space.builder()
                .ownerId(userId)
                .teamId(teamId)
                .type(SpaceType.TEAM)
                .name("Design Team")
                .build();
        space.setId(spaceId);

        when(teamMemberRepository.findByUserIdAndStatus(userId, TeamMemberStatus.ACTIVE))
                .thenReturn(List.of(membership));
        when(teamRepository.findAllById(List.of(teamId))).thenReturn(List.of(team));
        when(spaceRepository.findByTeamIdIn(List.of(teamId))).thenReturn(List.of(space));
        when(teamMemberRepository.countByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE)).thenReturn(1L);

        List<TeamSummaryResponse> responses = teamService.listMyTeams(userId);

        assertEquals(1, responses.size());
        assertEquals(teamId, responses.get(0).getId());
        assertEquals(spaceId, responses.get(0).getSpaceId());
        assertNotNull(responses.get(0).getCreatedAt());
    }
}
