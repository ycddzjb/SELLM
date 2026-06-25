package com.sellm.teaching;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TeachingContentRepository {
    private final TeachingContentMapper mapper;
    public TeachingContentRepository(TeachingContentMapper mapper) { this.mapper = mapper; }

    public TeachingContent save(TeachingContent c) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", c.getOwnerId());
        row.put("contentType", c.getContentType());
        row.put("title", c.getTitle());
        row.put("options", c.getOptions());
        row.put("aiDraft", c.getAiDraft());
        row.put("content", c.getContent());
        row.put("status", c.getStatus());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public TeachingContent findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<TeachingContent> listByOwnerAndType(Long ownerId, String contentType) {
        List<TeachingContent> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerAndType(ownerId, contentType)) out.add(fromRow(row));
        return out;
    }

    public void update(TeachingContent c) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", c.getId());
        row.put("title", c.getTitle());
        row.put("aiDraft", c.getAiDraft());
        row.put("content", c.getContent());
        row.put("status", c.getStatus());
        mapper.update(row);
    }

    private TeachingContent fromRow(Map<String, Object> row) {
        TeachingContent c = new TeachingContent();
        c.setId(((Number) row.get("id")).longValue());
        c.setOwnerId(((Number) row.get("ownerId")).longValue());
        c.setContentType((String) row.get("contentType"));
        c.setTitle((String) row.get("title"));
        c.setOptions((String) row.get("options"));
        c.setAiDraft((String) row.get("aiDraft"));
        c.setContent((String) row.get("content"));
        c.setStatus((String) row.get("status"));
        return c;
    }
}
