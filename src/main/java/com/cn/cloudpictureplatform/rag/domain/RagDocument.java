package com.cn.cloudpictureplatform.rag.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rag_document")
public class RagDocument extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private String contentType;

    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(nullable = false)
    private Integer version = 1;

    private UUID supersededBy;

    @Column(nullable = false)
    private Boolean deleted = false;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DocumentChunk> chunks = new ArrayList<>();

    @Version
    private Long versionLock;
}
