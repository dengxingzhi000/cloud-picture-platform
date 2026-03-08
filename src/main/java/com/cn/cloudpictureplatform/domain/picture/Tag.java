package com.cn.cloudpictureplatform.domain.picture;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "tag",
        indexes = {
                @Index(name = "idx_tag_name", columnList = "name")
        }
)
public class Tag extends BaseEntity {

    @Column(nullable = false, length = 80, unique = true)
    private String name;
}
