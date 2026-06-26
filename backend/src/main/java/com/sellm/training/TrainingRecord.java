package com.sellm.training;

/** 训练数据:多模态训练记录 + 教师录入/采纳的指标得分(scores JSON)。 */
public class TrainingRecord {
    private Long id;
    private Long cycleId;
    private String mediaType;   // TEXT / IMAGE / VIDEO / AUDIO
    private String objectKey;
    private String transcript;  // 多模态识别结果
    private String noteText;
    private String scores;      // JSON:[{item,score,maxScore}]

    public TrainingRecord() {}

    public TrainingRecord(Long id, Long cycleId, String mediaType, String objectKey,
                          String transcript, String noteText, String scores) {
        this.id = id;
        this.cycleId = cycleId;
        this.mediaType = mediaType;
        this.objectKey = objectKey;
        this.transcript = transcript;
        this.noteText = noteText;
        this.scores = scores;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
    public String getScores() { return scores; }
    public void setScores(String scores) { this.scores = scores; }
}
