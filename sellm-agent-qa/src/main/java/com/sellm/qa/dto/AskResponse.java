package com.sellm.qa.dto;

import java.util.List;
import java.util.Map;

public class AskResponse {
    private Long conversationId;
    private String answer;
    private String routeTo;   // null | assessment/teaching/aids/research
    private String deepLink;  // null | 前端深链
    private List<Map<String, String>> sources;
    private Long messageId;

    public Long getConversationId() { return conversationId; }
    public void setConversationId(Long conversationId) { this.conversationId = conversationId; }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getRouteTo() { return routeTo; }
    public void setRouteTo(String routeTo) { this.routeTo = routeTo; }
    public String getDeepLink() { return deepLink; }
    public void setDeepLink(String deepLink) { this.deepLink = deepLink; }
    public List<Map<String, String>> getSources() { return sources; }
    public void setSources(List<Map<String, String>> sources) { this.sources = sources; }
    public Long getMessageId() { return messageId; }
    public void setMessageId(Long messageId) { this.messageId = messageId; }
}
