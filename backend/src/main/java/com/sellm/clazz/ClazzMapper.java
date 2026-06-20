package com.sellm.clazz;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ClazzMapper {

    void insert(Map<String, Object> row);

    Map<String, Object> findById(@Param("id") Long id);

    List<Map<String, Object>> findByOrg(@Param("orgId") Long orgId);

    void update(Map<String, Object> row);

    void deleteById(@Param("id") Long id);
}
