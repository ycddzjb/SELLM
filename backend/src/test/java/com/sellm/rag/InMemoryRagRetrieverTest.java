package com.sellm.rag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class InMemoryRagRetrieverTest {

    private InMemoryRagRetriever retrieverWithDocs() {
        return new InMemoryRagRetriever(List.of(
            new KnowledgeDoc("d1", "孤独症社交干预策略:结构化教学", "手册A"),
            new KnowledgeDoc("d2", "CARS 量表解读:总分与分段", "手册B"),
            new KnowledgeDoc("d3", "言语训练通用方法", "手册C")
        ));
    }

    @Test
    void 按关键词召回相关文档() {
        List<KnowledgeDoc> docs = retrieverWithDocs().retrieve("CARS 解读", 2);
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getDocId()).isEqualTo("d2");
    }

    @Test
    void 限制返回数量为topK() {
        List<KnowledgeDoc> docs = retrieverWithDocs().retrieve("干预 解读 训练", 2);
        assertThat(docs).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void 无匹配时返回空列表() {
        List<KnowledgeDoc> docs = retrieverWithDocs().retrieve("微积分", 3);
        assertThat(docs).isEmpty();
    }
}
