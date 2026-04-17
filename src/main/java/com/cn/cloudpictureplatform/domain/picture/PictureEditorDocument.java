package com.cn.cloudpictureplatform.domain.picture;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import java.util.UUID;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "picture_editor_document",
        indexes = {
                @Index(name = "idx_picture_editor_document_picture", columnList = "picture_id")
        }
)
public class PictureEditorDocument extends BaseEntity {

    @Column(name = "picture_id", nullable = false, columnDefinition = "uuid", unique = true)
    private UUID pictureId;

    @Column(nullable = false)
    private long version;

    @Version
    @Column(name = "row_version", nullable = false)
    private Long rowVersion;

    @Lob
    @Column(name = "document_content", nullable = false, columnDefinition = "clob")
    private String documentContent;

    @Column(name = "last_updated_by_user_id", columnDefinition = "uuid")
    private UUID lastUpdatedByUserId;
}
