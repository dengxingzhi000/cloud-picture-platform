package com.cn.cloudpictureplatform.rag.domain;

import com.cn.cloudpictureplatform.common.model.BaseEntity;
import jakarta.persistence.*;
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
@Table(name = "rag_document_chunk")
public class DocumentChunk extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private RagDocument document;

    @Column(nullable = false)
    private Integer chunkIndex = 0;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(nullable = false)
    private Integer tokenCount;

    @Column(length = 500)
    private String title;

    @Column(length = 1000)
    private String sectionPath;

    private Integer pageNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChunkStatus status = ChunkStatus.ACTIVE;

    @Column(nullable = false)
    private Integer chunkVersion = 1;

    @Column(name = "opensearch_id", length = 100)
    private String openSearchId;

    @Column(nullable = false)
    private Integer embeddingDim = 1024;
}
