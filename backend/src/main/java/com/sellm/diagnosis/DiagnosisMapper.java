package com.sellm.diagnosis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface DiagnosisMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    List<Map<String, Object>> findByChildId(@Param("childId") Long childId);
    void updateResult(@Param("id") Long id, @Param("dimensions") String dimensions, @Param("draft") String draft);
    void updateDraftEdited(@Param("id") Long id, @Param("draft") String draft);
    void updateFinalized(@Param("id") Long id, @Param("content") String content);
}
