package com.sellm.assessment.media;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface EvaluationMediaMapper {

    void insert(Map<String, Object> row);

    Map<String, Object> findById(@Param("id") Long id);

    List<Map<String, Object>> findByChild(@Param("childId") Long childId);

    List<Map<String, Object>> findByChildAndType(@Param("childId") Long childId,
                                                 @Param("mediaType") String mediaType);

    void updateStatus(@Param("id") Long id, @Param("status") String status);
}
