package com.sellm.child;

import com.sellm.common.crypto.FieldCipher;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ChildRepository {

    private final ChildMapper mapper;
    private final FieldCipher cipher;

    public ChildRepository(ChildMapper mapper, FieldCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    public Child save(Child child) {
        Map<String, Object> row = new HashMap<>();
        row.put("nameEnc", cipher.encrypt(child.getName()));
        row.put("disorderType", child.getDisorderType());
        row.put("orgId", child.getOrgId());
        row.put("guardianUserId", child.getGuardianUserId());
        mapper.insert(row);
        Object id = row.get("id");
        child.setId(((Number) id).longValue());
        return child;
    }

    public Child findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) {
            return null;
        }
        return toChild(row);
    }

    public List<Child> findAll() {
        List<Child> list = new ArrayList<>();
        for (Map<String, Object> row : mapper.findAll()) {
            list.add(toChild(row));
        }
        return list;
    }

    public boolean update(Child child) {
        if (mapper.findById(child.getId()) == null) {
            return false;
        }
        Map<String, Object> row = new HashMap<>();
        row.put("id", child.getId());
        row.put("nameEnc", cipher.encrypt(child.getName()));
        row.put("disorderType", child.getDisorderType());
        row.put("orgId", child.getOrgId());
        row.put("guardianUserId", child.getGuardianUserId());
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

    private Child toChild(Map<String, Object> row) {
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        Long guardian = row.get("guardianUserId") == null ? null : ((Number) row.get("guardianUserId")).longValue();
        return new Child(((Number) row.get("id")).longValue(),
            cipher.decrypt((String) row.get("nameEnc")),
            (String) row.get("disorderType"), orgId, guardian);
    }
}
