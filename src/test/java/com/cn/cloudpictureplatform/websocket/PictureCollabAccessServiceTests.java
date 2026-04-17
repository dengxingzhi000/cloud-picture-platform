package com.cn.cloudpictureplatform.websocket;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;

@ExtendWith(MockitoExtension.class)
class PictureCollabAccessServiceTests {

    @Mock
    private PictureAssetRepository pictureAssetRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private TeamMemberRepository teamMemberRepository;

    private PictureCollabAccessService service;

    @BeforeEach
    void setUp() {
        service = new PictureCollabAccessService(
                pictureAssetRepository,
                spaceRepository,
                teamMemberRepository
        );
    }

    @Test
    void shouldAllowActiveTeamMemberToAccessTeamPicture() {
        UUID pictureId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PictureAsset picture = PictureAsset.builder()
                .ownerId(UUID.randomUUID())
                .spaceId(spaceId)
                .visibility(Visibility.TEAM)
                .reviewStatus(ReviewStatus.APPROVED)
                .name("team-picture")
                .originalFilename("team-picture.jpg")
                .storageKey("team-picture.jpg")
                .sizeBytes(128L)
                .build();
        picture.setId(pictureId);

        Space space = Space.builder()
                .ownerId(UUID.randomUUID())
                .teamId(teamId)
                .type(SpaceType.TEAM)
                .name("team-space")
                .build();
        space.setId(spaceId);

        TeamMember member = TeamMember.builder()
                .teamId(teamId)
                .userId(userId)
                .status(TeamMemberStatus.ACTIVE)
                .build();

        when(pictureAssetRepository.findById(pictureId)).thenReturn(Optional.of(picture));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.of(member));

        assertTrue(service.canAccess(pictureId, userId, UserRole.USER));
    }

    @Test
    void shouldDenyNonMemberAccessToTeamPicture() {
        UUID pictureId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PictureAsset picture = PictureAsset.builder()
                .ownerId(UUID.randomUUID())
                .spaceId(spaceId)
                .visibility(Visibility.TEAM)
                .reviewStatus(ReviewStatus.APPROVED)
                .name("team-picture")
                .originalFilename("team-picture.jpg")
                .storageKey("team-picture.jpg")
                .sizeBytes(128L)
                .build();
        picture.setId(pictureId);

        Space space = Space.builder()
                .ownerId(UUID.randomUUID())
                .teamId(teamId)
                .type(SpaceType.TEAM)
                .name("team-space")
                .build();
        space.setId(spaceId);

        when(pictureAssetRepository.findById(pictureId)).thenReturn(Optional.of(picture));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, userId)).thenReturn(Optional.empty());

        assertFalse(service.canAccess(pictureId, userId, UserRole.USER));
    }
}
