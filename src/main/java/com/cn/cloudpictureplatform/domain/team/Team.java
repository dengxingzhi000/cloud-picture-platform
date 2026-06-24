package com.cn.cloudpictureplatform.domain.team;

import com.cn.cloudpictureplatform.common.exception.ApiException;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import com.cn.cloudpictureplatform.common.web.ApiErrorCode;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "team",
        indexes = {
                @Index(name = "idx_team_owner", columnList = "owner_id")
        }
)
public class Team extends BaseEntity {

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(length = 200)
    private String description;

    // ── Aggregate Root 业务方法 ──────────────────────────────

    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "team name is required");
        }
        this.name = newName.trim();
    }

    public void updateDescription(String newDescription) {
        this.description = (newDescription != null && !newDescription.isBlank())
                ? newDescription.trim() : null;
    }

    public void transferOwnership(UUID newOwnerId) {
        if (newOwnerId == null) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST, "new owner ID is required");
        }
        this.ownerId = newOwnerId;
    }

    public boolean isOwnedBy(UUID userId) {
        return this.ownerId.equals(userId);
    }
}
