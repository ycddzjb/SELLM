package com.sellm.training;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.Map;

@Mapper
public interface StageEvalMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    Map<String, Object> findByCycleId(@Param("cycleId") Long cycleId);
    void updateDraftEdited(@Param("id") Long id, @Param("draft") String draft);
    void updateFinalized(@Param("id") Long id, @Param("content") String content);
}
