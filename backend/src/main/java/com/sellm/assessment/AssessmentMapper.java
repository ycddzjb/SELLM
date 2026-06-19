package com.sellm.assessment;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface AssessmentMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    List<Map<String, Object>> findByChildId(@Param("childId") Long childId);
}
