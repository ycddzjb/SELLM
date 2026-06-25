package com.sellm.aids;

/**
 * 智能层返回的生成结果:文本描述 + 可选媒体二进制。
 * media 为 null 时仅有文本(如 VIDEO 首版降级);非 null 时由 AssetGenerationTask 按 ext/mimeType 落盘。
 */
public class GeneratedContent {
    private final String text;
    private final byte[] media;
    private final String mimeType;
    private final String ext;

    public GeneratedContent(String text, byte[] media, String mimeType, String ext) {
        this.text = text;
        this.media = media;
        this.mimeType = mimeType;
        this.ext = ext;
    }

    /** 纯文本结果(无媒体)。 */
    public static GeneratedContent text(String text) {
        return new GeneratedContent(text, null, null, null);
    }

    public String getText() { return text; }
    public byte[] getMedia() { return media; }
    public String getMimeType() { return mimeType; }
    public String getExt() { return ext; }
    public boolean hasMedia() { return media != null && media.length > 0; }
}
