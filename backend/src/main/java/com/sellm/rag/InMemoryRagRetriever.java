package com.sellm.rag;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
public class InMemoryRagRetriever implements RagRetriever {

    private final List<KnowledgeDoc> docs;

    public InMemoryRagRetriever(List<KnowledgeDoc> docs) {
        this.docs = docs;
    }

    @Override
    public List<KnowledgeDoc> retrieve(String query, int topK) {
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
