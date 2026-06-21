package com.sellm.qa;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface QaMessageMapper {
    void insert(Map<String, Object> row);
    List<Map<String, Object>> findByConversationId(Long conversationId);
}
