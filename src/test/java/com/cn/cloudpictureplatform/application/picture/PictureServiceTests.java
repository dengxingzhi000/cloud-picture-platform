package com.cn.cloudpictureplatform.application.picture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import com.cn.cloudpictureplatform.application.search.SearchIndexService;
import com.cn.cloudpictureplatform.domain.audit.ModerationRecord;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.team.Team;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.team.TeamRole;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.domain.storage.StorageResult;
import com.cn.cloudpictureplatform.domain.storage.StorageService;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.ModerationRecordRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureTagRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TagRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamRepository;
import com.cn.cloudpictureplatform.interfaces.picture.dto.PictureDetailResponse;
import com.cn.cloudpictureplatform.websocket.NotificationPublisher;

@ExtendWith(MockitoExtension.class)
class PictureServiceTests {

    @Mock
    private StorageService storageService;
    @Mock
    private PictureAssetRepository pictureAssetRepository;
    @Mock
    private SpaceRepository spaceRepository;
    @Mock
    private ModerationRecordRepository moderationRecordRepository;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private PictureTagRepository pictureTagRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private SearchIndexService searchIndexService;
    @Mock
    private TeamMemberRepository teamMemberRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private NotificationPublisher notificationPublisher;

    private PictureService pictureService;

    @BeforeEach
    void setUp() {
        pictureService = new PictureService(
                storageService,
                pictureAssetRepository,
                spaceRepository,
                moderationRecordRepository,
                appUserRepository,
                pictureTagRepository,
                tagRepository,
                searchIndexService,
                teamMemberRepository,
                teamRepository,
                notificationPublisher
        );
    }

    @Test
    void shouldReturnTeamPictureDetailForActiveMember() {
        UUID pictureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        PictureAsset picture = PictureAsset.builder()
                .ownerId(ownerId)
                .spaceId(spaceId)
                .visibility(Visibility.TEAM)
                .reviewStatus(ReviewStatus.APPROVED)
                .name("design-board")
                .originalFilename("board.png")
                .storageKey("pictures/board.png")
                .sizeBytes(1024L)
                .build();
        picture.setId(pictureId);

        Space space = Space.builder()
                .ownerId(ownerId)
                .teamId(teamId)
                .type(SpaceType.TEAM)
                .name("Design Team Space")
                .build();
        space.setId(spaceId);

        TeamMember member = TeamMember.builder()
                .teamId(teamId)
                .userId(requesterId)
                .role(TeamRole.ADMIN)
                .status(TeamMemberStatus.ACTIVE)
                .build();

        Team team = Team.builder()
                .ownerId(ownerId)
                .name("Design Team")
                .build();
        team.setId(teamId);

        AppUser owner = AppUser.builder()
                .username("owner")
                .displayName("Owner")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        owner.setId(ownerId);

        when(pictureAssetRepository.findById(pictureId)).thenReturn(Optional.of(picture));
        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(space));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, requesterId)).thenReturn(Optional.of(member));
        when(teamRepository.findById(teamId)).thenReturn(Optional.of(team));
        when(appUserRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(pictureTagRepository.findByPictureAssetIdOrderByCreatedAtDesc(pictureId)).thenReturn(List.of());

        PictureDetailResponse response = pictureService.getPictureDetail(pictureId, requesterId, UserRole.USER);

        assertEquals(pictureId, response.getId());
        assertEquals("Design Team", response.getTeamName());
        assertTrue(response.isCanEdit());
        assertTrue(response.isCanManage());
        assertTrue(response.isCanJoinCollaboration());
    }

    @Test
    void shouldNotifyOwnerAfterReview() {
        UUID pictureId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();

        PictureAsset picture = PictureAsset.builder()
                .ownerId(ownerId)
                .spaceId(UUID.randomUUID())
                .visibility(Visibility.PUBLIC)
                .reviewStatus(ReviewStatus.PENDING)
                .name("public-photo")
                .originalFilename("public-photo.jpg")
                .storageKey("pictures/public-photo.jpg")
                .sizeBytes(1024L)
                .build();
        picture.setId(pictureId);

        AppUser owner = AppUser.builder()
                .username("alice")
                .displayName("Alice")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        owner.setId(ownerId);

        when(pictureAssetRepository.findById(pictureId)).thenReturn(Optional.of(picture));
        when(pictureAssetRepository.save(any(PictureAsset.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        pictureService.review(pictureId, reviewerId, ReviewStatus.APPROVED, "looks good");

        verify(moderationRecordRepository).save(any(ModerationRecord.class));
        verify(searchIndexService).enqueuePicture(pictureId);
        verify(notificationPublisher).notifyReviewDecision(
                "alice",
                pictureId,
                "public-photo",
                true,
                "looks good"
        );
    }

    @Test
    void shouldNotifyOwnerAndAdminsAfterPublicUpload() {
        UUID ownerId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();

        Space personalSpace = Space.builder()
                .ownerId(ownerId)
                .type(SpaceType.PERSONAL)
                .name("Personal Space")
                .usedBytes(0L)
                .build();
        personalSpace.setId(spaceId);

        AppUser owner = AppUser.builder()
                .username("alice")
                .displayName("Alice")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        owner.setId(ownerId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "public-photo.jpg",
                "image/jpeg",
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );

        when(spaceRepository.findFirstByOwnerIdAndType(ownerId, SpaceType.PERSONAL))
                .thenReturn(Optional.of(personalSpace));
        when(storageService.store(any(), any())).thenReturn(new StorageResult(
                "users/" + ownerId + "/pictures/public-photo.jpg",
                "https://cdn.example.com/public-photo.jpg",
                file.getSize(),
                "image/jpeg"
        ));
        when(pictureAssetRepository.save(any(PictureAsset.class))).thenAnswer(invocation -> {
            PictureAsset saved = invocation.getArgument(0);
            saved.setId(pictureId);
            return saved;
        });
        when(spaceRepository.save(any(Space.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findById(ownerId)).thenReturn(Optional.of(owner));

        pictureService.upload(ownerId, file, Visibility.PUBLIC, "Public Photo", null);

        verify(searchIndexService).enqueuePicture(pictureId);
        verify(notificationPublisher).notifyUploadCompleted("alice", pictureId, "Public Photo");
        verify(notificationPublisher).notifyAdminNewUpload(pictureId, "Public Photo", "alice");
        verify(notificationPublisher, never()).notifyTeamPictureUploaded(any(), any(), any(), any());
    }

    @Test
    void shouldNotifyTeamMembersAfterTeamUpload() {
        UUID ownerId = UUID.randomUUID();
        UUID teammateId = UUID.randomUUID();
        UUID pictureId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();

        Space teamSpace = Space.builder()
                .ownerId(ownerId)
                .teamId(teamId)
                .type(SpaceType.TEAM)
                .name("Design Team Space")
                .usedBytes(0L)
                .build();
        teamSpace.setId(spaceId);

        TeamMember ownerMembership = TeamMember.builder()
                .teamId(teamId)
                .userId(ownerId)
                .role(TeamRole.OWNER)
                .status(TeamMemberStatus.ACTIVE)
                .build();
        TeamMember teammateMembership = TeamMember.builder()
                .teamId(teamId)
                .userId(teammateId)
                .role(TeamRole.MEMBER)
                .status(TeamMemberStatus.ACTIVE)
                .build();

        AppUser owner = AppUser.builder()
                .username("alice")
                .displayName("Alice")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        owner.setId(ownerId);

        AppUser teammate = AppUser.builder()
                .username("bob")
                .displayName("Bob")
                .passwordHash("hash")
                .role(UserRole.USER)
                .build();
        teammate.setId(teammateId);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "team-photo.jpg",
                "image/jpeg",
                "not-an-image".getBytes(StandardCharsets.UTF_8)
        );

        when(spaceRepository.findById(spaceId)).thenReturn(Optional.of(teamSpace));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, ownerId)).thenReturn(Optional.of(ownerMembership));
        when(teamMemberRepository.findByTeamIdAndStatus(teamId, TeamMemberStatus.ACTIVE))
                .thenReturn(List.of(ownerMembership, teammateMembership));
        when(storageService.store(any(), any())).thenReturn(new StorageResult(
                "users/" + ownerId + "/pictures/team-photo.jpg",
                "https://cdn.example.com/team-photo.jpg",
                file.getSize(),
                "image/jpeg"
        ));
        when(pictureAssetRepository.save(any(PictureAsset.class))).thenAnswer(invocation -> {
            PictureAsset saved = invocation.getArgument(0);
            saved.setId(pictureId);
            return saved;
        });
        when(spaceRepository.save(any(Space.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(appUserRepository.findById(ownerId)).thenReturn(Optional.of(owner));
        when(appUserRepository.findById(teammateId)).thenReturn(Optional.of(teammate));

        pictureService.upload(ownerId, file, Visibility.TEAM, "Team Photo", spaceId);

        verify(notificationPublisher).notifyUploadCompleted("alice", pictureId, "Team Photo");
        verify(notificationPublisher, never()).notifyAdminNewUpload(any(), any(), any());
        verify(notificationPublisher).notifyTeamPictureUploaded(
                eq(List.of("bob")),
                eq(pictureId),
                eq("Team Photo"),
                eq("alice")
        );
    }
}

