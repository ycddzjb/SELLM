package com.sellm.aids.dto;

/** 素材产物视图(GET /assets/{id} 与列表)。 */
public class AssetResponse {
    private final Long id;
    private final String type;
    private final String status;
    private final String storageKey;
    private final String mimeType;
    private final String error;

    public AssetResponse(Long id, String type, String status, String storageKey, String mimeType, String error) {
        this.id = id;
        this.type = type;
        this.status = status;
        this.storageKey = storageKey;
        this.mimeType = mimeType;
        this.error = error;
    }

    public Long getId() { return id; }
    public String getType() { return type; }
    public String getStatus() { return status; }
    public String getStorageKey() { return storageKey; }
    public String getMimeType() { return mimeType; }
    public String getError() { return error; }
}
