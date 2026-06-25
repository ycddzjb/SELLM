package com.sellm.qa;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QaConversationRepository {
    private final QaConversationMapper mapper;
    public QaConversationRepository(QaConversationMapper mapper) { this.mapper = mapper; }

    public QaConversation save(QaConversation c) {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", c.getUserId());
        row.put("title", c.getTitle());
        mapper.insert(row);
        c.setId(((Number) row.get("id")).longValue());
        return c;
    }

    public QaConversation findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<QaConversation> listByUser(Long userId) {
        List<QaConversation> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByUserId(userId)) out.add(fromRow(row));
        return out;
    }

    private QaConversation fromRow(Map<String, Object> row) {
        QaConversation c = new QaConversation();
        c.setId(((Number) row.get("id")).longValue());
        c.setUserId(((Number) row.get("userId")).longValue());
        c.setTitle((String) row.get("title"));
        return c;
    }
}
