package com.cn.cloudpictureplatform.application.space;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.domain.space.SpaceType;
import com.cn.cloudpictureplatform.domain.team.TeamMember;
import com.cn.cloudpictureplatform.domain.team.TeamMemberStatus;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;
import com.cn.cloudpictureplatform.infrastructure.persistence.TeamMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 空间权限验证工具类
 *
 * @author Deng
 * createData 2026/4/22 16:22
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class SpacePermissionValidator {
    private final SpaceRepository spaceRepository;
    private final TeamMemberRepository teamMemberRepository;

    /**
     * 解析并验证空间访问权限
     *
     * @param ownerId 用户ID
     * @param spaceId 空间ID（可为null，此时查找用户的个人空间）
     * @return 验证通过的空间对象
     * @throws ApiException 空间不存在或无访问权限时抛出异常
     */
    public Space resolveAndValidateSpace(UUID ownerId, UUID spaceId) {
        if (spaceId == null) {
            return spaceRepository.findFirstByOwnerIdAndType(ownerId, SpaceType.PERSONAL)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
        } else {
            Space space = spaceRepository.findById(spaceId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
            validateTeamSpaceAccess(space, ownerId);
            return space;
        }
    }

    /**
     * 验证团队空间的访问权限
     *
     * @param space  空间对象
     * @param userId 用户ID
     * @throws ApiException 如果不是团队空间或用户无访问权限时抛出异常
     */
    public void validateTeamSpaceAccess(Space space, UUID userId) {
        if (space.getType() != SpaceType.TEAM) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "invalid target space");
        }
        TeamMember member = teamMemberRepository.findByTeamIdAndUserId(space.getTeamId(), userId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.FORBIDDEN, "not a member of this team"));
        if (member.getStatus() != TeamMemberStatus.ACTIVE) {
            throw new ApiException(ApiErrorCode.FORBIDDEN, "not an active team member");
        }
    }
}
