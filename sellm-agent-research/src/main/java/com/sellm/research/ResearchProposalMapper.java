package com.sellm.research;

import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface ResearchProposalMapper {
    void insert(Map<String, Object> row);
    Map<String, Object> findById(Long id);
    List<Map<String, Object>> findByOwnerId(Long ownerId);
    void update(Map<String, Object> row);
}
