package com.sellm.qa;

public class QaMessage {
    private Long id;
    private Long conversationId;
    private String role;       // USER | ASSISTANT
    private String content;
    private String routeTo;    // null | assessment/teaching/aids/research
    private String sources;    // JSON 字符串数组,默认 "[]"

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getRouteTo() { return routeTo; }
    public void setRouteTo(String routeTo) { this.routeTo = routeTo; }
    public String getSources() { return sources; }
    public void setSources(String sources) { this.sources = sources; }
}
