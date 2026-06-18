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
        List<KnowledgeDoc> docs = mapper.findAll();
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
