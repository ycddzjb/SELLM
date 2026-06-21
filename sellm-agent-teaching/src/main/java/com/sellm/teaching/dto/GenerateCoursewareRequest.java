package com.sellm.teaching.dto;

public class GenerateCoursewareRequest {
    private Long lessonPlanId;
    private String format; // 可空,默认 TEXT
    public Long getLessonPlanId() { return lessonPlanId; }
    public void setLessonPlanId(Long lessonPlanId) { this.lessonPlanId = lessonPlanId; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
}
