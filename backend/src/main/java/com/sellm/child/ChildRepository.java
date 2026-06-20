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
        row.put("nameEnc", child.getName() == null ? null : cipher.encrypt(child.getName()));
        row.put("disorderType", child.getDisorderType());
        row.put("orgId", child.getOrgId());
        row.put("guardianUserId", child.getGuardianUserId());
        putExtended(row, child);
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
        putExtended(row, child);
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
        Object nameEnc = row.get("nameEnc");
        Child c = new Child(((Number) row.get("id")).longValue(),
            nameEnc == null ? null : cipher.decrypt((String) nameEnc),
            (String) row.get("disorderType"), orgId, guardian);
        c.setBaselineSummary((String) row.get("baselineSummary"));
        c.setAnnualIepSummary((String) row.get("annualIepSummary"));
        c.setMonthlyGoal((String) row.get("monthlyGoal"));
        c.setReassessDate(dateStr(row.get("reassessDate")));
        c.setIepDueDate(dateStr(row.get("iepDueDate")));
        c.setInterventionProgress((String) row.get("interventionProgress"));
        return c;
    }

    /** 扩展字段写入 row(姓名外的明文概要 + 日期字符串)。 */
    private void putExtended(Map<String, Object> row, Child child) {
        row.put("baselineSummary", child.getBaselineSummary());
        row.put("annualIepSummary", child.getAnnualIepSummary());
        row.put("monthlyGoal", child.getMonthlyGoal());
        row.put("reassessDate", child.getReassessDate());
        row.put("iepDueDate", child.getIepDueDate());
        row.put("interventionProgress", child.getInterventionProgress());
    }

    /** DATE 列回读可能是 java.sql.Date / String,统一转 ISO yyyy-MM-dd 字符串。 */
    private String dateStr(Object v) {
        return v == null ? null : v.toString();
    }
}
