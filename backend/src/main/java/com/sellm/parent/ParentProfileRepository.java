package com.sellm.parent;

import com.sellm.common.crypto.FieldCipher;
import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class ParentProfileRepository {

    private final ParentProfileMapper mapper;
    private final FieldCipher cipher;

    public ParentProfileRepository(ParentProfileMapper mapper, FieldCipher cipher) {
        this.mapper = mapper;
        this.cipher = cipher;
    }

    public void save(ParentProfile p) {
        Map<String, Object> row = new HashMap<>();
        row.put("userId", p.getUserId());
        row.put("nameEnc", p.getName() == null ? null : cipher.encrypt(p.getName()));
        row.put("relationship", p.getRelationship());
        row.put("assignedTeacherId", p.getAssignedTeacherId());
        row.put("childNameEnc", p.getChildName() == null ? null : cipher.encrypt(p.getChildName()));
        row.put("childDisorderType", p.getChildDisorderType());
        row.put("classId", p.getClassId());
        row.put("childId", p.getChildId());
        mapper.insert(row);
    }

    public ParentProfile findByUserId(Long userId) {
        Map<String, Object> row = mapper.findByUserId(userId);
        if (row == null) {
            return null;
        }
        return new ParentProfile(
            ((Number) row.get("userId")).longValue(),
            decrypt(row.get("nameEnc")),
            (String) row.get("relationship"),
            asLong(row.get("assignedTeacherId")),
            decrypt(row.get("childNameEnc")),
            (String) row.get("childDisorderType"),
            asLong(row.get("classId")),
            asLong(row.get("childId")));
    }

    public void updateChildId(Long userId, Long childId) {
        mapper.updateChildId(userId, childId);
    }

    public List<ParentProfileRow> listByOrg(Long orgId) {
        return toRows(mapper.findByOrg(orgId));
    }

    public List<ParentProfileRow> listPendingByTeacher(Long teacherId) {
        return toRows(mapper.findPendingByTeacher(teacherId));
    }

    private List<ParentProfileRow> toRows(List<Map<String, Object>> rows) {
        List<ParentProfileRow> list = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            ParentProfileRow row = new ParentProfileRow();
            row.setUserId(asLong(r.get("userId")));
            row.setUsername((String) r.get("username"));
            row.setStatus((String) r.get("status"));
            row.setName(decrypt(r.get("nameEnc")));
            row.setRelationship((String) r.get("relationship"));
            row.setAssignedTeacherId(asLong(r.get("assignedTeacherId")));
            row.setChildName(decrypt(r.get("childNameEnc")));
            row.setChildDisorderType((String) r.get("childDisorderType"));
            row.setClassId(asLong(r.get("classId")));
            row.setClassName((String) r.get("className"));
            row.setChildId(asLong(r.get("childId")));
            list.add(row);
        }
        return list;
    }

    private String decrypt(Object enc) {
        return enc == null ? null : cipher.decrypt((String) enc);
    }

    private Long asLong(Object v) {
        return v == null ? null : ((Number) v).longValue();
    }
}
