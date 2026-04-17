package com.cn.cloudpictureplatform.websocket;

import java.util.UUID;
import org.springframework.stereotype.Service;
import com.cn.cloudpictureplatform.domain.picture.PictureAsset;
import com.cn.cloudpictureplatform.domain.picture.ReviewStatus;
import com.cn.cloudpictureplatform.domain.picture.Visibility;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.domain.user.UserRole;
import com.cn.cloudpictureplatform.infrastructure.persistence.PictureAssetRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;

@Service
public class PictureCollabAccessService {

    private final PictureAssetRepository pictureAssetRepository;
    private final SpaceRepository spaceRepository;
    private final TeamMemberRepository teamMemberRepository;

    public PictureCollabAccessService(
            PictureAssetRepository pictureAssetRepository,
            SpaceRepository spaceRepository,
            TeamMemberRepository teamMemberRepository
    ) {
        this.pictureAssetRepository = pictureAssetRepository;
        this.spaceRepository = spaceRepository;
        this.teamMemberRepository = teamMemberRepository;
    }

    public boolean canAccess(UUID pictureId, UUID userId, UserRole role) {
        if (pictureId == null || userId == null) {
            return false;
        }
        if (role == UserRole.ADMIN) {
            return true;
        }

        PictureAsset asset = pictureAssetRepository.findById(pictureId).orElse(null);
        if (asset == null) {
            return false;
        }
        if (userId.equals(asset.getOwnerId())) {
            return true;
        }
        if (asset.getVisibility() == Visibility.PUBLIC && asset.getReviewStatus() == ReviewStatus.APPROVED) {
            return true;
        }

        Space space = spaceRepository.findById(asset.getSpaceId()).orElse(null);
        if (space == null || space.getType() != SpaceType.TEAM || space.getTeamId() == null) {
            return false;
        }
        if (asset.getVisibility() == Visibility.PRIVATE) {
            return false;
        }

        return teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), userId)
                .filter(member -> member.getStatus() == TeamMemberStatus.ACTIVE)
                .isPresent();
    }
}
