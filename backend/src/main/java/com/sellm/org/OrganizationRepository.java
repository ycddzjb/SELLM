package com.sellm.org;

import org.springframework.stereotype.Repository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OrganizationRepository {

    private final OrganizationMapper mapper;

    public OrganizationRepository(OrganizationMapper mapper) {
        this.mapper = mapper;
    }

    public Organization save(Organization org) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", org.getName());
        row.put("region", org.getRegion());
        mapper.insert(row);
        org.setId(((Number) row.get("id")).longValue());
        return org;
    }

    public Organization findById(Long id) {
        Map<String, Object> row = mapper.findById(id);
        if (row == null) return null;
        return new Organization(((Number) row.get("id")).longValue(),
            (String) row.get("name"), (String) row.get("region"));
    }

    /** 所有机构,按 id 升序。供超管列表与注册选机构的公开列表。 */
    public List<Organization> listAll() {
        List<Organization> list = new ArrayList<>();
        for (Map<String, Object> row : mapper.findAll()) {
            list.add(new Organization(((Number) row.get("id")).longValue(),
                (String) row.get("name"), (String) row.get("region")));
        }
        return list;
    }

    /** 取机构名;机构不存在时返回兜底名,供报告/IEP 生成时不至于失败 */
    public String nameOf(Long orgId) {
        if (orgId == null) return "未知机构";
        Organization org = findById(orgId);
        return org == null ? "未知机构" : org.getName();
    }
}
