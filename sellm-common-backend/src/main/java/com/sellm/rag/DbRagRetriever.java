package com.sellm.rag;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Primary
@Component
public class DbRagRetriever implements RagRetriever {

    private final KnowledgeDocMapper mapper;

    public DbRagRetriever(KnowledgeDocMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<KnowledgeDoc> retrieve(String query, int topK) {
        return rank(mapper.findAll(), query, topK);
    }

    @Override
    public List<KnowledgeDoc> retrieveByCategory(String query, String category, int topK) {
        if (category == null || category.isBlank()) {
            return retrieve(query, topK);
        }
        return rank(mapper.findByCategory(category), query, topK);
    }

    /** 关键词命中计数排序,取 topK(第一版;后续换向量检索)。 */
    private List<KnowledgeDoc> rank(List<KnowledgeDoc> docs, String query, int topK) {
        String[] terms = query.trim().split("\\s+");
        List<Scored> scored = new ArrayList<>();
        for (KnowledgeDoc doc : docs) {
            int score = 0;
            for (String term : terms) {
                if (!term.isBlank() && doc.getContent().contains(term)) {
                    score++;
                }
            }
            if (score > 0) {
                scored.add(new Scored(doc, score));
            }
        }
        scored.sort(Comparator.comparingInt((Scored s) -> s.score).reversed());

        List<KnowledgeDoc> result = new ArrayList<>();
        for (int i = 0; i < Math.min(topK, scored.size()); i++) {
            result.add(scored.get(i).doc);
        }
        return result;
    }

    private static class Scored {
        final KnowledgeDoc doc;
        final int score;
        Scored(KnowledgeDoc doc, int score) {
            this.doc = doc;
            this.score = score;
        }
    }
}
