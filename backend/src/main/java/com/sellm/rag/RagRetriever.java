package com.sellm.rag;

import java.util.List;

public interface RagRetriever {
    /** 召回与 query 最相关的至多 topK 篇文档 */
    List<KnowledgeDoc> retrieve(String query, int topK);
}
