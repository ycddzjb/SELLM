package com.sellm.rag;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DbRagRetrieverTest {

    @Autowired
    private DbRagRetriever retriever;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM knowledge_doc");
        jdbc.update("INSERT INTO knowledge_doc(doc_id, category, content, source) VALUES (?,?,?,?)",
            "d1", "IEP_CASE", "孤独症社交干预策略:结构化教学", "手册A");
        jdbc.update("INSERT INTO knowledge_doc(doc_id, category, content, source) VALUES (?,?,?,?)",
            "d2", "SCALE_SYSTEM", "CARS 量表解读:总分与分段", "手册B");
        jdbc.update("INSERT INTO knowledge_doc(doc_id, category, content, source) VALUES (?,?,?,?)",
            "d3", "POLICY_ETHICS", "言语训练通用方法 禁止体罚", "手册C");
    }

    @Test
    void 从库按关键词召回相关文档() {
        List<KnowledgeDoc> docs = retriever.retrieve("CARS 解读", 2);
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getDocId()).isEqualTo("d2");
    }

    @Test
    void 限制返回数量为topK() {
        List<KnowledgeDoc> docs = retriever.retrieve("干预 解读 训练", 2);
        assertThat(docs).hasSizeLessThanOrEqualTo(2);
    }

    @Test
    void 无匹配返回空列表() {
        assertThat(retriever.retrieve("微积分", 3)).isEmpty();
    }

    @Test
    void 按分类召回只返回该类文档() {
        List<KnowledgeDoc> docs = retriever.retrieveByCategory("训练", "POLICY_ETHICS", 5);
        assertThat(docs).isNotEmpty();
        assertThat(docs).allMatch(d -> "POLICY_ETHICS".equals(d.getCategory()));
        assertThat(docs.get(0).getDocId()).isEqualTo("d3");
    }

    @Test
    void 按分类召回不串类() {
        List<KnowledgeDoc> docs = retriever.retrieveByCategory("训练", "SCALE_SYSTEM", 5);
        assertThat(docs).noneMatch(d -> "d3".equals(d.getDocId()));
    }

    @Test
    void category为空退化为全库检索() {
        List<KnowledgeDoc> docs = retriever.retrieveByCategory("CARS 解读", null, 2);
        assertThat(docs).isNotEmpty();
        assertThat(docs.get(0).getDocId()).isEqualTo("d2");
    }
}
