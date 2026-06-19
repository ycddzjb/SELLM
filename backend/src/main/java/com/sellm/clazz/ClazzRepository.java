package com.sellm.clazz;

import com.sellm.common.DisorderType;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ClazzRepository {

    private final ClazzMapper mapper;

    public ClazzRepository(ClazzMapper mapper) {
        this.mapper = mapper;
    }

    public Clazz save(Clazz clazz) {
        DisorderType.validateCsv(clazz.getDisorderTypes());

        Map<String, Object> row = new HashMap<>();
        row.put("name", clazz.getName());
        row.put("orgId", clazz.getOrgId());
        row.put("disorderTypes", clazz.getDisorderTypes());
        mapper.insert(row);
        clazz.setId(((Number) row.get("id")).longValue());
        return clazz;
    }

    public Clazz findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) {
            return null;
        }
        return toClazz(row);
    }

    public List<Clazz> listByOrg(Long orgId) {
        List<Clazz> list = new ArrayList<>();
        for (Map<String, Object> row : mapper.findByOrg(orgId)) {
            list.add(toClazz(row));
        }
        return list;
    }

    public boolean update(Clazz clazz) {
        if (mapper.findById(clazz.getId()) == null) {
            return false;
        }
        DisorderType.validateCsv(clazz.getDisorderTypes());

        Map<String, Object> row = new HashMap<>();
        row.put("id", clazz.getId());
        row.put("name", clazz.getName());
        row.put("orgId", clazz.getOrgId());
        row.put("disorderTypes", clazz.getDisorderTypes());
        mapper.update(row);
        return true;
    }

    public boolean deleteById(Long id) {
        if (mapper.findById(id) == null) {
            return false;
        }
        mapper.deleteById(id);
        return true;
    }

    private Clazz toClazz(Map<String, Object> row) {
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        return new Clazz(((Number) row.get("id")).longValue(),
            (String) row.get("name"), orgId, (String) row.get("disorderTypes"));
    }
}
