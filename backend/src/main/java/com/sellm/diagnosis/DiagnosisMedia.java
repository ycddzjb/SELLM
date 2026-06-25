package com.sellm.diagnosis;

/** 诊断关联的多模态素材;transcript 存识别结果(ASR 转写/影像描述)。 */
public class DiagnosisMedia {
    private Long id;
    private Long diagnosisId;
    private String mediaType;   // TEXT / IMAGE / VIDEO / AUDIO
    private String objectKey;   // 对象存储 key;纯文本可空
    private String transcript;  // 识别结果
    private String noteText;

    public DiagnosisMedia() {}

    public DiagnosisMedia(Long id, Long diagnosisId, String mediaType, String objectKey,
                          String transcript, String noteText) {
        this.id = id;
        this.diagnosisId = diagnosisId;
        this.mediaType = mediaType;
        this.objectKey = objectKey;
        this.transcript = transcript;
        this.noteText = noteText;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDiagnosisId() { return diagnosisId; }
    public void setDiagnosisId(Long diagnosisId) { this.diagnosisId = diagnosisId; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getTranscript() { return transcript; }
    public void setTranscript(String transcript) { this.transcript = transcript; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
}
