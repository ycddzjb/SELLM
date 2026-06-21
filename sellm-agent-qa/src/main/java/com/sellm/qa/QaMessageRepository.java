package com.sellm.qa;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class QaMessageRepository {
    private final QaMessageMapper mapper;
    public QaMessageRepository(QaMessageMapper mapper) { this.mapper = mapper; }

    public QaMessage save(QaMessage m) {
        Map<String, Object> row = new HashMap<>();
        row.put("conversationId", m.getConversationId());
        row.put("role", m.getRole());
        row.put("content", m.getContent());
        row.put("routeTo", m.getRouteTo());
        row.put("sources", m.getSources() == null ? "[]" : m.getSources());
        mapper.insert(row);
        m.setId(((Number) row.get("id")).longValue());
        return m;
    }

    public List<QaMessage> listByConversation(Long conversationId) {
        List<QaMessage> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByConversationId(conversationId)) {
            QaMessage m = new QaMessage();
            m.setId(((Number) row.get("id")).longValue());
            m.setConversationId(((Number) row.get("conversationId")).longValue());
            m.setRole((String) row.get("role"));
            m.setContent((String) row.get("content"));
            m.setRouteTo((String) row.get("routeTo"));
            m.setSources((String) row.get("sources"));
            out.add(m);
        }
        return out;
    }
}
