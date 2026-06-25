package com.sellm.user.dto;

/** 管理者激活微信家长:补全孩子信息(及可选班级)。机构取管理者本机构。 */
public class ActivateWeChatRequest {
    private String childName;
    private String childDisorderType;  // 单码或 CSV(DisorderType 校验)
    private Long classId;              // 可空

    public String getChildName() { return childName; }
    public void setChildName(String childName) { this.childName = childName; }
    public String getChildDisorderType() { return childDisorderType; }
    public void setChildDisorderType(String childDisorderType) { this.childDisorderType = childDisorderType; }
    public Long getClassId() { return classId; }
    public void setClassId(Long classId) { this.classId = classId; }
}
