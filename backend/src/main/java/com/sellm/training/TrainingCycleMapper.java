package com.sellm.training;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TrainingCycleMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(@Param("id") Long id);
    List<Map<String, Object>> findByChildId(@Param("childId") Long childId);
    Integer findMaxSeqByChild(@Param("childId") Long childId);
    Map<String, Object> findByChildAndSeq(@Param("childId") Long childId, @Param("seq") int seq);
    void updateStatus(@Param("id") Long id, @Param("status") String status);
    void updateIepId(@Param("id") Long id, @Param("iepId") Long iepId);
}
