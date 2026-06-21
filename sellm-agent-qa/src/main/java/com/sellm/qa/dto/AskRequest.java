package com.sellm.qa.dto;

public class AskRequest {
    private Long conversationId; // 可空 → 新建会话
    private String question;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
}
