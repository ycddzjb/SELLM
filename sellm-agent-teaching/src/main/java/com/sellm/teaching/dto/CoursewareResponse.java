package com.sellm.teaching.dto;

public class CoursewareResponse {
    private Long id;
    private Long lessonPlanId;
    private String status;
    private String content;
    private String storageKey;
    private String format;

    public CoursewareResponse(Long id, Long lessonPlanId, String status, String content, String storageKey, String format) {
        this.id = id; this.lessonPlanId = lessonPlanId; this.status = status;
        this.content = content; this.storageKey = storageKey; this.format = format;
    }
    public Long getId() { return id; }
    public Long getLessonPlanId() { return lessonPlanId; }
    public String getStatus() { return status; }
    public String getContent() { return content; }
    public String getStorageKey() { return storageKey; }
    public String getFormat() { return format; }
}
