package com.sellm.child.log;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChildLogMapper {

    void insert(Map<String, Object> row);

    Map<String, Object> findById(@Param("id") Long id);

    List<Map<String, Object>> findByChild(@Param("childId") Long childId);

    List<Map<String, Object>> findByChildAndType(@Param("childId") Long childId,
                                                 @Param("logType") String logType);

    void deleteById(@Param("id") Long id);
}
