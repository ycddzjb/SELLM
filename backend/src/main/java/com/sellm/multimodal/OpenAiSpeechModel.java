package com.sellm.multimodal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 真实 ASR(OpenAI/通义兼容)。仅在 sellm.speech.provider=openai 且配 key 时装配。
 * 用 multipart 上传音频转写;HTTP/1.1 避免 HTTP/2 协商卡死(同 vision)。
 * "发请求"抽成 protected send(...) 便于测试子类化注入假响应,不真连网。
 */
public class OpenAiSpeechModel implements SpeechModel {

    private final SpeechProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;

    public OpenAiSpeechModel(SpeechProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    }

    @Override
    public String transcribe(byte[] audio, String hint) {
        try {
            String resp = send(audio);
            JsonNode root = json.readTree(resp);
            JsonNode text = root.path("text");
            return text.isMissingNode() ? "[ASR 无结果]" : text.asText();
        } catch (Exception e) {
            return "[ASR 调用失败] 请教师据录音补充文字描述。";
        }
    }

    /** 实际发请求(multipart/form-data 上传音频);protected 便于测试注入假响应,不真连网。 */
    protected String send(byte[] audio) throws Exception {
        String boundary = "----sellmAsr" + System.nanoTime();
        String CRLF = "\r\n";
        var head = new StringBuilder();
        head.append("--").append(boundary).append(CRLF)
            .append("Content-Disposition: form-data; name=\"model\"").append(CRLF).append(CRLF)
            .append(props.getModel()).append(CRLF)
            .append("--").append(boundary).append(CRLF)
            .append("Content-Disposition: form-data; name=\"file\"; filename=\"audio\"").append(CRLF)
            .append("Content-Type: application/octet-stream").append(CRLF).append(CRLF);
        byte[] headBytes = head.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] tailBytes = (CRLF + "--" + boundary + "--" + CRLF).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] bodyBytes = new byte[headBytes.length + (audio == null ? 0 : audio.length) + tailBytes.length];
        System.arraycopy(headBytes, 0, bodyBytes, 0, headBytes.length);
        if (audio != null) System.arraycopy(audio, 0, bodyBytes, headBytes.length, audio.length);
        System.arraycopy(tailBytes, 0, bodyBytes, headBytes.length + (audio == null ? 0 : audio.length), tailBytes.length);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(props.getBaseUrl() + "/v1/audio/transcriptions"))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .header("Content-Type", "multipart/form-data; boundary=" + boundary)
            .header("Authorization", "Bearer " + props.getApiKey())
            .POST(HttpRequest.BodyPublishers.ofByteArray(bodyBytes))
            .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new RuntimeException("ASR 返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
