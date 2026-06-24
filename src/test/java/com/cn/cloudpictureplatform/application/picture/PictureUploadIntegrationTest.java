package com.cn.cloudpictureplatform.application.picture;

import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.user.AppUser;
import com.cn.cloudpictureplatform.domain.user.UserStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.AppUserRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class PictureUploadIntegrationTest {

    @Autowired
    private PictureUploadService pictureUploadService;

    @Autowired
    private DeduplicationPictureUploadService deduplicationPictureUploadService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PictureAssetRepository pictureAssetRepository;

    @Autowired
    private SpaceRepository spaceRepository;

    private AppUser testUser;
    private UUID spaceId;

    @BeforeEach
    void setUp() {
        testUser = AppUser.builder()
                .username("upload-test-" + UUID.randomUUID().toString().substring(0, 8))
                .email("upload-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com")
                .passwordHash("$2a$10$dummy")
                .displayName("Upload Test User")
                .status(UserStatus.ACTIVE)
                .build();
        testUser = appUserRepository.save(testUser);

        Space space = spaceRepository.findFirstByOwnerIdAndType(testUser.getId(), SpaceType.PERSONAL)
                .orElseGet(() -> spaceRepository.save(Space.builder()
                        .ownerId(testUser.getId())
                        .type(SpaceType.PERSONAL)
                        .name("Personal")
                        .quotaBytes(10L * 1024 * 1024 * 1024)
                        .usedBytes(0L)
                        .build()));
        spaceId = space.getId();
    }

    @Test
    void shouldUploadPictureSuccessfully() {
        byte[] imageBytes = createTestImageBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", imageBytes);

        var response = pictureUploadService.upload(
                testUser.getId(), file, Visibility.PRIVATE, "Test Picture", null);

        assertNotNull(response);
        assertNotNull(response.getId());

        var saved = pictureAssetRepository.findById(response.getId());
        assertTrue(saved.isPresent());
        assertEquals("Test Picture", saved.get().getName());
        assertEquals(Visibility.PRIVATE, saved.get().getVisibility());
        assertEquals(ReviewStatus.APPROVED, saved.get().getReviewStatus());
        assertEquals(testUser.getId(), saved.get().getOwnerId());
    }

    @Test
    void shouldSetPendingReviewForPublicUpload() {
        byte[] imageBytes = createTestImageBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "public.jpg", "image/jpeg", imageBytes);

        var response = pictureUploadService.upload(
                testUser.getId(), file, Visibility.PUBLIC, "Public Picture", null);

        var saved = pictureAssetRepository.findById(response.getId()).orElseThrow();
        assertEquals(Visibility.PUBLIC, saved.getVisibility());
        assertEquals(ReviewStatus.PENDING, saved.getReviewStatus());
    }

    @Test
    void shouldDedupIdenticalFiles() {
        byte[] imageBytes = createTestImageBytes();
        MockMultipartFile file1 = new MockMultipartFile(
                "file", "test1.jpg", "image/jpeg", imageBytes);
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "test2.jpg", "image/jpeg", imageBytes);

        var response1 = deduplicationPictureUploadService.uploadWithDeduplication(
                testUser.getId(), file1, Visibility.PRIVATE, "Picture 1", null);
        var response2 = deduplicationPictureUploadService.uploadWithDeduplication(
                testUser.getId(), file2, Visibility.PRIVATE, "Picture 2", null);

        assertNotNull(response1);
        assertNotNull(response2);
        assertNotEquals(response1.getId(), response2.getId());

        var pic1 = pictureAssetRepository.findById(response1.getId()).orElseThrow();
        var pic2 = pictureAssetRepository.findById(response2.getId()).orElseThrow();
        assertEquals(pic1.getFileContentId(), pic2.getFileContentId());
    }

    @Test
    void shouldRejectEmptyFile() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);

        assertThrows(Exception.class, () ->
                pictureUploadService.upload(
                        testUser.getId(), emptyFile, Visibility.PRIVATE, "Empty", null));
    }

    private byte[] createTestImageBytes() {
        return new byte[]{
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0,
                0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01,
                0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
                (byte) 0xFF, (byte) 0xD9
        };
    }
}
