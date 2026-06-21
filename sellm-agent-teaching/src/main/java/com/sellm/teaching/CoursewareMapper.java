package com.sellm.teaching;

import org.apache.ibatis.annotations.Mapper;
import java.util.Map;

@Mapper
public interface CoursewareMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    void update(Map<String, Object> row);
}
