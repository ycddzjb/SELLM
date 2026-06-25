package com.sellm.qa;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface QaConversationMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    List<Map<String, Object>> findByUserId(Long userId);
}
