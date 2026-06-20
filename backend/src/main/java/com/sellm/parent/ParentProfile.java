package com.sellm.parent;

/**
 * 家长档案:扩展信息 + 待建儿童暂存。
 * 领域层 name/childName 为明文,Repository 负责加密落库与解密读取。
 */
public class ParentProfile {
    private Long userId;          // = app_user.id
    private String name;          // 家长姓名(明文)
    private String relationship;  // 亲戚关系码(Relationship)
    private Long assignedTeacherId; // 注册所选审核老师
    private String childName;     // 待建儿童姓名(明文)
    private String childDisorderType;
    private Long classId;         // 所在班级
    private Long childId;         // 审核通过后回填

    public ParentProfile() {
    }

    public ParentProfile(Long userId, String name, String relationship, Long assignedTeacherId,
                         String childName, String childDisorderType, Long classId, Long childId) {
        this.userId = userId;
        this.name = name;
        this.relationship = relationship;
        this.assignedTeacherId = assignedTeacherId;
        this.childName = childName;
        this.childDisorderType = childDisorderType;
        this.classId = classId;
        this.childId = childId;
    }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
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
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
}
