package com.sellm.rag;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface KnowledgeDocMapper {

    /** 读取全部知识文档(第一版库内规模小,全量加载;后续换向量库) */
    List<KnowledgeDoc> findAll();
}
