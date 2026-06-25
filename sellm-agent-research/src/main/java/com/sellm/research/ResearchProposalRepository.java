package com.sellm.research;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ResearchProposalRepository {
    private final ResearchProposalMapper mapper;
    public ResearchProposalRepository(ResearchProposalMapper mapper) { this.mapper = mapper; }

    public ResearchProposal save(ResearchProposal p) {
        Map<String, Object> row = new HashMap<>();
        row.put("ownerId", p.getOwnerId());
        row.put("topic", p.getTopic());
        row.put("aiDraft", p.getAiDraft());
        row.put("content", p.getContent());
        row.put("status", p.getStatus());
        mapper.insert(row);
        p.setId(((Number) row.get("id")).longValue());
        return p;
    }

    public ResearchProposal findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        return row == null ? null : fromRow(row);
    }

    public List<ResearchProposal> listByOwner(Long ownerId) {
        List<ResearchProposal> out = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOwnerId(ownerId)) out.add(fromRow(row));
        return out;
    }

    public void update(ResearchProposal p) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", p.getId());
        row.put("content", p.getContent());
        row.put("aiDraft", p.getAiDraft());
        row.put("status", p.getStatus());
        mapper.update(row);
    }

    private ResearchProposal fromRow(Map<String, Object> row) {
        ResearchProposal p = new ResearchProposal();
        p.setId(((Number) row.get("id")).longValue());
        p.setOwnerId(((Number) row.get("ownerId")).longValue());
        p.setTopic((String) row.get("topic"));
        p.setAiDraft((String) row.get("aiDraft"));
        p.setContent((String) row.get("content"));
        p.setStatus((String) row.get("status"));
        return p;
    }
}
