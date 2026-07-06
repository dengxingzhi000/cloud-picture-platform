package com.cn.cloudpictureplatform.rag.domain;

import java.util.List;

public record RetrievalResult(
    String chunkId,
    String documentId,
    String content,
    String title,
    String sectionPath,
    int pageNumber,
    double rrfScore,
    double bm25Score,
    double vectorScore,
    List<String> sourceLabels
) {}
