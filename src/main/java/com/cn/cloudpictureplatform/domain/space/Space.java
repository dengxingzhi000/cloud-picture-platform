package com.cn.cloudpictureplatform.domain.space;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
        name = "picture_space",
        indexes = {
                @Index(name = "idx_space_owner", columnList = "owner_id"),
                @Index(name = "idx_space_type", columnList = "type"),
                @Index(name = "idx_space_team", columnList = "team_id")
        }
)
public class Space extends BaseEntity {

    @Column(name = "owner_id", nullable = false, columnDefinition = "uuid")
    private UUID ownerId;

    @Column(name = "team_id", columnDefinition = "uuid")
    private UUID teamId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SpaceType type = SpaceType.PERSONAL;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "quota_bytes", nullable = false)
    private long quotaBytes = 0L;

    @Column(name = "used_bytes", nullable = false)
    private long usedBytes = 0L;
}
