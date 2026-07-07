package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.List;

@Component
public class DocumentParser {

    private final Tika tika = new Tika();

    public ParsedDocument parse(InputStream inputStream, String filename, String contentType) {
        try {
            Metadata metadata = new Metadata();
            metadata.set("title", filename);
            String text = tika.parseToString(inputStream, metadata);
            String title = metadata.get("title") != null ? metadata.get("title") : filename;
            return new ParsedDocument(
                title,
                contentType,
                List.of(new ParsedDocument.Page(1, text, List.of()))
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse document: " + filename, e);
        }
    }
}