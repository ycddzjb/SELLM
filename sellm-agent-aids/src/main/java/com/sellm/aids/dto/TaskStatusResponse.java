package com.sellm.aids.dto;

/** GET /tasks/{taskId} 轮询响应:status + (SUCCESS 时 result) + (FAILED 时 error)。 */
public class TaskStatusResponse {
    private final String status;
    private final Result result;
    private final String error;

    public TaskStatusResponse(String status, Result result, String error) {
        this.status = status;
        this.result = result;
        this.error = error;
    }

    public String getStatus() { return status; }
    public Result getResult() { return result; }
    public String getError() { return error; }

    /** SUCCESS 时的产物引用。 */
    public static class Result {
        private final String type;
        private final String storageKey;
        private final String mimeType;
        public Result(String type, String storageKey, String mimeType) {
            this.type = type;
            this.storageKey = storageKey;
            this.mimeType = mimeType;
        }
        public String getType() { return type; }
        public String getStorageKey() { return storageKey; }
        public String getMimeType() { return mimeType; }
    }
}
