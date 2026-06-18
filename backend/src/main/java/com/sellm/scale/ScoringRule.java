package com.sellm.scale;

import java.util.List;

public class ScoringRule {
    private final List<ScoreBand> bands;

    public ScoringRule(List<ScoreBand> bands) {
        this.bands = bands;
    }

    public List<ScoreBand> getBands() { return bands; }
}
