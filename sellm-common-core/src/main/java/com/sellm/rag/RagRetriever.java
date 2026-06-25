package com.sellm.rag;

import java.util.List;

public interface RagRetriever {
    /** 召回与 query 最相关的至多 topK 篇文档 */
    List<KnowledgeDoc> retrieve(String query, int topK);

    /**
     * 按分类召回(category 为 SCALE_SYSTEM/IEP_CASE/POLICY_ETHICS;null 表示不限分类)。
     * 默认实现退化为全库检索,便于旧实现兼容。
     */
    default List<KnowledgeDoc> retrieveByCategory(String query, String category, int topK) {
        return retrieve(query, topK);
    }
}
