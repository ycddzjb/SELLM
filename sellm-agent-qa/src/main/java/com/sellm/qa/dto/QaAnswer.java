package com.sellm.qa.dto;

import java.util.List;
import java.util.Map;

public class QaAnswer {
    private String answer;
    private List<Map<String, String>> sources;

    public QaAnswer() {}
    public QaAnswer(String answer, List<Map<String, String>> sources) {
        this.answer = answer;
        this.sources = sources;
    }
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public List<Map<String, String>> getSources() { return sources; }
    public void setSources(List<Map<String, String>> sources) { this.sources = sources; }
}
