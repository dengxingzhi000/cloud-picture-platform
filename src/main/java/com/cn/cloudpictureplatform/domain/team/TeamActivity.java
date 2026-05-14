package com.cn.cloudpictureplatform.domain.team;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;
import org.springframework.data.annotation.CreatedDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "team_activity", indexes = {
        @Index(name = "idx_ta_team", columnList = "team_id, created_at desc")
})
public class TeamActivity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "team_id", nullable = false, columnDefinition = "uuid")
    private UUID teamId;

    @Column(name = "actor_id", nullable = false, columnDefinition = "uuid")
    private UUID actorId;

    @Column(name = "activity_type", nullable = false, length = 50)
    private String activityType;

    @Column(name = "target_id", columnDefinition = "uuid")
    private UUID targetId;

    @Column(columnDefinition = "jsonb")
    private String payload;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
