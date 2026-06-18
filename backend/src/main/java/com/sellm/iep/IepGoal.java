package com.sellm.iep;

public class IepGoal {
    private final String description; // 目标描述
    private final String term;        // "长期" / "短期"

    public IepGoal(String description, String term) {
        this.description = description;
        this.term = term;
    }

    public String getDescription() { return description; }
    public String getTerm() { return term; }
}
