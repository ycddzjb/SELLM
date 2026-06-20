package com.sellm.child.log;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ChildLogRepository {

    private final ChildLogMapper mapper;

    public ChildLogRepository(ChildLogMapper mapper) {
        this.mapper = mapper;
    }

    public ChildLog save(ChildLog log) {
        Map<String, Object> row = new HashMap<>();
        row.put("childId", log.getChildId());
        row.put("logType", log.getLogType());
        row.put("content", log.getContent());
        row.put("authorUserId", log.getAuthorUserId());
        mapper.insert(row);
        log.setId(((Number) row.get("id")).longValue());
        return log;
    }

    public ChildLog findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : toLog(row);
    }

    public List<ChildLog> listByChild(Long childId) {
        return toList(mapper.findByChild(childId));
    }

    public List<ChildLog> listByChildAndType(Long childId, String logType) {
        return toList(mapper.findByChildAndType(childId, logType));
    }

    public void deleteById(Long id) {
        mapper.deleteById(id);
    }

    private List<ChildLog> toList(List<Map<String, Object>> rows) {
        List<ChildLog> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            list.add(toLog(r));
        }
        return list;
    }

    private ChildLog toLog(Map<String, Object> row) {
        Long childId = row.get("childId") == null ? null : ((Number) row.get("childId")).longValue();
        Long author = row.get("authorUserId") == null ? null : ((Number) row.get("authorUserId")).longValue();
        Object createdAt = row.get("createdAt");
        return new ChildLog(((Number) row.get("id")).longValue(), childId,
            (String) row.get("logType"), (String) row.get("content"),
            author, createdAt == null ? null : createdAt.toString());
    }
}
