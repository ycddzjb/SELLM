package com.sellm.config;

import com.sellm.rag.KnowledgeDoc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class RagConfig {

    /**
     * 脚手架 bean:满足 InMemoryRagRetriever 的构造依赖,使上下文可启动。
     * Task 6 引入 DbRagRetriever(@Primary)后,知识来源改为数据库。
     */
    @Bean
    public List<KnowledgeDoc> knowledgeDocs() {
        return new ArrayList<>();
    }
}
