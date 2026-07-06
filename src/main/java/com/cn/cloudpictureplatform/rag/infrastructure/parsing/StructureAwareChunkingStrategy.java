package com.cn.cloudpictureplatform.rag.infrastructure.parsing;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StructureAwareChunkingStrategy {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
        "^(#{1,6})\\s+(.+)$", Pattern.MULTILINE
    );

    private final int maxTokens;
    private final int overlapTokens;

    public StructureAwareChunkingStrategy(
            @org.springframework.beans.factory.annotation.Value("${app.rag.chunking.max-tokens:512}") int maxTokens,
            @org.springframework.beans.factory.annotation.Value("${app.rag.chunking.overlap-tokens:50}") int overlapTokens) {
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<TextChunk> chunkWithStructure(String text, String sectionPrefix) {
        List<String> sections = splitByHeadings(text);
        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = 0;

        for (String section : sections) {
            String sectionTitle = extractHeading(section);
            String fullPrefix = sectionPrefix != null
                ? sectionPrefix + " > " + (sectionTitle != null ? sectionTitle : "")
                : (sectionTitle != null ? sectionTitle : "");

            String body = removeHeading(section);
            List<TextChunk> sectionChunks = chunkBody(body, fullPrefix, chunkIndex);
            chunks.addAll(sectionChunks);
            chunkIndex += sectionChunks.size();
        }

        return chunks;
    }

    private List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                sections.add(text.substring(lastEnd, matcher.start()));
            }
            lastEnd = matcher.start();
        }

        if (lastEnd < text.length()) {
            sections.add(text.substring(lastEnd));
        }

        return sections.isEmpty() ? List.of(text) : sections;
    }

    private String extractHeading(String section) {
        Matcher matcher = HEADING_PATTERN.matcher(section);
        return matcher.find() ? matcher.group(2).trim() : null;
    }

    private String removeHeading(String section) {
        return HEADING_PATTERN.matcher(section).replaceFirst("").trim();
    }

    private List<TextChunk> chunkBody(String body, String sectionPath, int startIndex) {
        if (body.isBlank()) {
            return List.of();
        }

        String[] words = body.split("\\s+");
        if (words.length <= maxTokens) {
            return List.of(new TextChunk(startIndex, body.trim(), words.length, 0, body.length()));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int chunkIndex = startIndex;
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + maxTokens, words.length);
            String chunkText = String.join(" ", List.of(words).subList(start, end)).trim();

            chunks.add(new TextChunk(chunkIndex++, chunkText, end - start, 0, chunkText.length()));

            int nextStart = end - overlapTokens;
            if (nextStart <= start) nextStart = end;
            start = nextStart;
        }

        return chunks;
    }
}