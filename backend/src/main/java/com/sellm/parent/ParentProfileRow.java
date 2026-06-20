package com.sellm.parent;

/** 家长列表行:profile + app_user + 班级名(姓名已解密)。供管理员列表 / 老师待审。 */
public class ParentProfileRow {
    private Long userId;
    private String username;
    private String status;
    private String name;          // 已解密
    private String relationship;  // 关系码
    private Long assignedTeacherId;
    private String childName;     // 已解密
    private String childDisorderType;
    private Long classId;
    private String className;
    private Long childId;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }
    public Long getAssignedTeacherId() { return assignedTeacherId; }
    public void setAssignedTeacherId(Long assignedTeacherId) { this.assignedTeacherId = assignedTeacherId; }
    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }
    public String getChildDisorderType() { return childDisorderType; }
    public void setChildDisorderType(String childDisorderType) { this.childDisorderType = childDisorderType; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
}
