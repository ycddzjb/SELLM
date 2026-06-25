package com.sellm.teaching.dto;

import java.util.List;

public class GenerateCoursewareRequest {
    private Long lessonPlanId;
    private String format; // 可空,默认 TEXT
    /** 需脱敏屏蔽的命名 PII;课件基于教案明文(含姓名)出网,须由调用方传入屏蔽表。 */
    private List<String> subjectNames;
    public Long getLessonPlanId() { return lessonPlanId; }
    public void setLessonPlanId(Long lessonPlanId) { this.lessonPlanId = lessonPlanId; }
    public String getFormat() { return format; }
    public void setFormat(String format) { this.format = format; }
    public List<String> getSubjectNames() { return subjectNames; }
    public void setSubjectNames(List<String> subjectNames) { this.subjectNames = subjectNames; }
}
