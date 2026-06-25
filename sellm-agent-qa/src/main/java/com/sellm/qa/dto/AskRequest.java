package com.sellm.qa.dto;

import java.util.List;

public class AskRequest {
    private Long conversationId; // 可空 → 新建会话
    private String question;
    /** 需脱敏屏蔽的命名 PII(如关联儿童/家长姓名);由调用方传入,正则脱敏对中文姓名无能为力。 */
    private List<String> subjectNames;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
}
