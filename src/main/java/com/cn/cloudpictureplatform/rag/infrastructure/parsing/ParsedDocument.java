package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import java.util.List;

public record ParsedDocument(
    String title,
    String contentType,
    List<Page> pages
) {
    public record Page(int pageNumber, String text, List<String> headings) {}
}