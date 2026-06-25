package com.sellm.teaching;

/** 教学模块统一内容(教案/课件/案例/习题);contentType 区分,options 存 JSON 选项。 */
public class TeachingContent {
    private Long id;
    private Long ownerId;
    private String contentType;   // LESSON/COURSEWARE/CASE/EXERCISE
    private String title;
    private String options;       // JSON 字符串
    private String aiDraft;
    private String content;
    private String status;        // DRAFT/FINALIZED

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOwnerId() { return ownerId; }
    public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }
    public String getAiDraft() { return aiDraft; }
    public void setAiDraft(String aiDraft) { this.aiDraft = aiDraft; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
