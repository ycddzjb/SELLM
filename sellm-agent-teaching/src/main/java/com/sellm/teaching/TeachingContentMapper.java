package com.sellm.teaching;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;
import java.util.Map;

@Mapper
public interface TeachingContentMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    List<Map<String, Object>> findByOwnerAndType(@Param("ownerId") Long ownerId, @Param("contentType") String contentType);
    void update(Map<String, Object> row);
}
