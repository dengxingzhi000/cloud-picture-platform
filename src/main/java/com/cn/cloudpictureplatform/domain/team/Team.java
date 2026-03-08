package com.cn.cloudpictureplatform.domain.team;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
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
}
