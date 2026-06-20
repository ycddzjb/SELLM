package com.sellm.user.dto;

public class RegisterRequest {
    private String username;
    private String password;
    private Long orgId;
    private String name;              // 家长姓名
    private String relationship;      // 亲戚关系码
    private String childName;         // 儿童姓名
    private String childDisorderType; // 儿童残障类型(单码)
    private Long assignedTeacherId;   // 所选审核老师
    private Long classId;             // 所在班级

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public Long getOrgId() { return orgId; }
    public void setOrgId(Long orgId) { this.orgId = orgId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }
    public String getChildDisorderType() { return childDisorderType; }
    public void setChildDisorderType(String childDisorderType) { this.childDisorderType = childDisorderType; }
    public Long getAssignedTeacherId() { return assignedTeacherId; }
    public void setAssignedTeacherId(Long assignedTeacherId) { this.assignedTeacherId = assignedTeacherId; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
