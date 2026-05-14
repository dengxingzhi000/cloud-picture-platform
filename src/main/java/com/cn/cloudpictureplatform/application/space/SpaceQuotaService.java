package com.cn.cloudpictureplatform.application.space;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import com.cn.cloudpictureplatform.domain.space.Space;
import com.cn.cloudpictureplatform.infrastructure.persistence.SpaceRepository;

@Service
public class SpaceQuotaService {
    private final SpaceRepository spaceRepository;

    public SpaceQuotaService(SpaceRepository spaceRepository) {
        this.spaceRepository = spaceRepository;
    }

    @Transactional(readOnly = true)
    public void assertQuotaAvailable(UUID spaceId, long fileSizeBytes) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
        if (space.getQuotaBytes() > 0
                && space.getUsedBytes() + fileSizeBytes > space.getQuotaBytes()) {
            long remaining = space.getQuotaBytes() - space.getUsedBytes();
            throw new ApiException(ApiErrorCode.QUOTA_EXCEEDED,
                    "space quota exceeded, remaining " + remaining + " bytes");
        }
    }

    @Transactional(readOnly = true)
    public SpaceUsageResponse getUsage(UUID spaceId) {
        Space space = spaceRepository.findById(spaceId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.NOT_FOUND, "space not found"));
        return new SpaceUsageResponse(
                spaceId,
                space.getQuotaBytes(),
                space.getUsedBytes(),
                space.getQuotaBytes() > 0
                        ? (double) space.getUsedBytes() / space.getQuotaBytes() * 100
                        : 0.0
        );
    }

    public record SpaceUsageResponse(
            UUID spaceId,
            long quotaBytes,
            long usedBytes,
            double usagePercent
    ) {}
}
