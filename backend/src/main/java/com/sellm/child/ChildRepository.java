package com.sellm.child;

import com.sellm.common.crypto.FieldCipher;
import org.springframework.stereotype.Repository;
import java.util.HashMap;
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
        String name = cipher.decrypt((String) row.get("nameEnc"));
        Long orgId = row.get("orgId") == null ? null : ((Number) row.get("orgId")).longValue();
        return new Child(((Number) row.get("id")).longValue(),
            name, (String) row.get("disorderType"), orgId);
    }
}
