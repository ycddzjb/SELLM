package com.sellm.user.dto;

import com.sellm.common.Relationship;
import com.sellm.parent.ParentProfileRow;
import com.sellm.security.Role;

/**
 * 家长列表项。
 * - 基础字段(id/username/role/orgId/status):阶段 A 起即有。
 * - 完整字段(name/relationship/childName/className):阶段 C 家长注册改造后填充。
 */
public class ParentResponse {
    private Long id;
    private String username;
    private Role role;
    private Long orgId;
    private String status;
    private String name;             // 家长姓名(已解密)
    private String relationship;     // 关系码
    private String relationshipLabel; // 关系中文
    private String childName;        // 儿童姓名(已解密)
    private String childDisorderType;
    private String className;        // 所在班级名

    public ParentResponse(Long id, String username, Role role, Long orgId, String status) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.orgId = orgId;
        this.status = status;
    }

    /** 从 parent_profile 行构造完整家长信息(角色固定 PARENT)。 */
    public static ParentResponse full(ParentProfileRow row) {
        ParentResponse r = new ParentResponse(row.getUserId(), row.getUsername(),
            Role.PARENT, null, row.getStatus());
        r.name = row.getName();
        r.relationship = row.getRelationship();
        r.relationshipLabel = Relationship.labelOf(row.getRelationship());
        r.childName = row.getChildName();
        r.childDisorderType = row.getChildDisorderType();
        r.className = row.getClassName();
        return r;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public Role getRole() { return role; }
    public Long getOrgId() { return orgId; }
    public String getStatus() { return status; }
    public String getName() { return name; }
    public String getRelationship() { return relationship; }
    public String getRelationshipLabel() { return relationshipLabel; }
    public String getChildName() { return childName; }
    public String getChildDisorderType() { return childDisorderType; }
    public String getClassName() { return className; }
}
