package com.sellm.report;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ReportRecordMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    List<Map<String, Object>> findByChildId(@Param("childId") Long childId);
    void updateFinalized(@Param("id") Long id, @Param("content") String content);
}
