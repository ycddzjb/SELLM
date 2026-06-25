package com.sellm.assessment.media.dto;

import com.sellm.assessment.media.EvaluationMedia;

public class MediaResponse {
    private final Long id;
    private final Long childId;
    private final String scaleId;
    private final String mediaType;
    private final String noteText;
    private final String status;

    public MediaResponse(Long id, Long childId, String scaleId, String mediaType,
                         String noteText, String status) {
        this.id = id;
        this.childId = childId;
        this.scaleId = scaleId;
        this.mediaType = mediaType;
        this.noteText = noteText;
        this.status = status;
    }

    public static MediaResponse of(EvaluationMedia m) {
        return new MediaResponse(m.getId(), m.getChildId(), m.getScaleId(),
            m.getMediaType(), m.getNoteText(), m.getStatus());
    }

    public Long getId() { return id; }
    public Long getChildId() { return childId; }
    public String getScaleId() { return scaleId; }
    public String getMediaType() { return mediaType; }
    public String getNoteText() { return noteText; }
    public String getStatus() { return status; }
}
