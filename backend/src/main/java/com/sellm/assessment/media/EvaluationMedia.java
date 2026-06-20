package com.sellm.assessment.media;

/** 评估媒体记录:多模态素材(图片/视频/笔记)的上传与识别状态。 */
public class EvaluationMedia {
    private Long id;
    private Long childId;
    private String scaleId;
    private String mediaType;      // IMAGE / VIDEO / NOTE
    private String objectKey;      // 对象存储 key;NOTE 可空
    private String noteText;
    private Long uploaderUserId;
    private String status;         // UPLOADED / ANALYZED

    public EvaluationMedia() {
    }

    public EvaluationMedia(Long id, Long childId, String scaleId, String mediaType,
                           String objectKey, String noteText, Long uploaderUserId, String status) {
        this.id = id;
        this.childId = childId;
        this.scaleId = scaleId;
        this.mediaType = mediaType;
        this.objectKey = objectKey;
        this.noteText = noteText;
        this.uploaderUserId = uploaderUserId;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getChildId() { return childId; }
    public void setChildId(Long childId) { this.childId = childId; }
    public String getScaleId() { return scaleId; }
    public void setScaleId(String scaleId) { this.scaleId = scaleId; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public String getObjectKey() { return objectKey; }
    public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
    public String getNoteText() { return noteText; }
    public void setNoteText(String noteText) { this.noteText = noteText; }
    public Long getUploaderUserId() { return uploaderUserId; }
    public void setUploaderUserId(Long uploaderUserId) { this.uploaderUserId = uploaderUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
