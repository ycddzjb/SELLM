package com.sellm.teaching.dto;

import com.sellm.teaching.TeachingContent;

/** 教学内容响应。 */
public class ContentResponse {
    private Long id;
    private String contentType;
    private String title;
    private String status;
    private String aiDraft;
    private String content;
    private String options;
    private Long sourceId;

    public static ContentResponse of(TeachingContent c) {
        ContentResponse r = new ContentResponse();
        r.id = c.getId();
        r.contentType = c.getContentType();
        r.title = c.getTitle();
        r.status = c.getStatus();
        r.aiDraft = c.getAiDraft();
        r.content = c.getContent();
        r.options = c.getOptions();
        r.sourceId = c.getSourceId();
        return r;
    }

    public Long getId() { return id; }
    public String getContentType() { return contentType; }
    public String getTitle() { return title; }
    public String getStatus() { return status; }
    public String getAiDraft() { return aiDraft; }
    public String getContent() { return content; }
    public String getOptions() { return options; }
    public Long getSourceId() { return sourceId; }
}
