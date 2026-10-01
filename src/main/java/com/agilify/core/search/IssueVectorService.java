package com.agilify.core.search;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Semantic issue dedup: embeds issue text into pgvector and finds near-duplicates by cosine similarity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IssueVectorService {

    private static final double SIMILARITY_THRESHOLD = 0.80;
    private static final int TOP_K = 3;

    private final VectorStore vectorStore;

    @Async
    public void indexIssue(UUID issueId, String projectKey, String title, String description) {
        try {
            String content = title + "\n" + (description == null ? "" : description);
            // fixed id == issueId so re-indexing on edit upserts instead of piling up stale duplicates
            Document document = new Document(issueId.toString(), content, Map.of(
                    "issueId", issueId.toString(),
                    "projectKey", projectKey
            ));
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            // ponytail: embedding model unreachable (e.g. Ollama not running) shouldn't block issue creation
            log.warn("Failed to index issue {} for dedup search: {}", issueId, e.getMessage());
        }
    }

    public List<UUID> findDuplicates(String projectKey, String draftText) {
        try {
            Filter.Expression filter = new FilterExpressionBuilder()
                    .eq("projectKey", projectKey)
                    .build();
            SearchRequest request = SearchRequest.builder()
                    .query(draftText)
                    .topK(TOP_K)
                    .similarityThreshold(SIMILARITY_THRESHOLD)
                    .filterExpression(filter)
                    .build();
            return vectorStore.similaritySearch(request).stream()
                    .map(doc -> UUID.fromString((String) doc.getMetadata().get("issueId")))
                    .toList();
        } catch (Exception e) {
            log.warn("Duplicate search failed: {}", e.getMessage());
            return List.of();
        }
    }
}
