package com.sellm.qa.dto;

import java.util.List;

public class ConversationView {
    private Long id;
    private String title;
    private List<MessageView> messages; // 列表查询时为 null

    public ConversationView(Long id, String title, List<MessageView> messages) {
        this.id = id; this.title = title; this.messages = messages;
    }
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public List<MessageView> getMessages() { return messages; }

    public static class MessageView {
        private final Long id;
        private final String role;
        private final String content;
        private final String routeTo;
        private final String sources;
        public MessageView(Long id, String role, String content, String routeTo, String sources) {
            this.id = id; this.role = role; this.content = content;
            this.routeTo = routeTo; this.sources = sources;
        }
        public Long getId() { return id; }
        public String getRole() { return role; }
        public String getContent() { return content; }
        public String getRouteTo() { return routeTo; }
        public String getSources() { return sources; }
    }
}
