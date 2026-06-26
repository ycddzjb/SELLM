package com.sellm.training.dto;

import com.sellm.training.TrainingRecord;

public class RecordResponse {
    private Long id;
    private Long cycleId;
    private String mediaType;
    private String transcript;
    private String noteText;
    private String scores;

    public static RecordResponse of(TrainingRecord r) {
        RecordResponse o = new RecordResponse();
        o.id = r.getId();
        o.cycleId = r.getCycleId();
        o.mediaType = r.getMediaType();
        o.transcript = r.getTranscript();
        o.noteText = r.getNoteText();
        o.scores = r.getScores();
        return o;
    }

    public Long getId() { return id; }
    public Long getCycleId() { return cycleId; }
    public String getMediaType() { return mediaType; }
    public String getTranscript() { return transcript; }
    public String getNoteText() { return noteText; }
    public String getScores() { return scores; }
}
