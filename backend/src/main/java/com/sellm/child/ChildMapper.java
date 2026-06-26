package com.sellm.child;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface ChildMapper {

    void insert(Map<String, Object> row);

    Map<String, Object> findById(@Param("id") Long id);

    List<Map<String, Object>> findAll();

    void update(Map<String, Object> row);

    void deleteById(@Param("id") Long id);

    long countByOrg(@Param("orgId") Long orgId);
}
