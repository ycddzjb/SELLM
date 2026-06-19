package com.sellm.assessment.dto;

import java.util.List;

public class SubmitAssessmentRequest {
    private Long childId;
    private String scaleId;
    private List<AnswerDto> answers;

    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public List<AnswerDto> getAnswers() { return answers; }
    public void setAnswers(List<AnswerDto> answers) { this.answers = answers; }

    public static class AnswerDto {
        private String itemId;
        private double score;
        public String getItemId() { return itemId; }
        public void setItemId(String itemId) { this.itemId = itemId; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
    }
}
