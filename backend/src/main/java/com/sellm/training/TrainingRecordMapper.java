package com.sellm.training;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TrainingRecordMapper {
    void insert(Map<String, Object> row);
    List<Map<String, Object>> findByCycleId(@Param("cycleId") Long cycleId);
}
