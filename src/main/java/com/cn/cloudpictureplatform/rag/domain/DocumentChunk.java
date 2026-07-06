package com.cn.cloudpictureplatform.rag.domain;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "rag_document_chunk")
public class DocumentChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private RagDocument document;

    @Column(nullable = false)
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private Integer tokenCount;

    private String title;

    private String sectionPath;

    private Integer pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkStatus status = ChunkStatus.ACTIVE;

    @Column(nullable = false)
    private Integer chunkVersion = 1;

    @Column(name = "opensearch_id")
    private String openSearchId;

    @Column(nullable = false)
    private Integer embeddingDim = 1024;
}
