package com.sellm.diagnosis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface DiagnosisMediaMapper {
    void insert(Map<String, Object> row);
    List<Map<String, Object>> findByDiagnosisId(@Param("diagnosisId") Long diagnosisId);
}
