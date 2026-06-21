package com.sellm.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sellm.qa.dto.QaAnswer;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** REST 调 Python 智能层 /v1/agents/qa/invoke。强制 HTTP/1.1(JDK 默认 HTTP/2 与部分网关协商卡死)。 */
@Component
public class HttpSmartLayerClient implements SmartLayerClient {

    private final SmartLayerProperties props;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient httpClient;

    public HttpSmartLayerClient(SmartLayerProperties props) {
        this.props = props;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .build();
    }

    @Override
    public QaAnswer generate(String anonymizedQuestion, int topK) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("question", anonymizedQuestion);
            body.put("topK", topK);
            String resp = send(json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            String answer = node.path("answer").asText("");
            List<Map<String, String>> sources = new ArrayList<>();
            JsonNode arr = node.path("sources");
            if (arr.isArray()) {
                for (JsonNode s : arr) {
                    Map<String, String> m = new HashMap<>();
                    m.put("title", s.path("title").asText(""));
                    m.put("source", s.path("source").asText(""));
                    sources.add(m);
                }
            }
            return new QaAnswer(answer, sources);
        } catch (Exception e) {
            throw new SmartLayerException("智能层调用失败", e);
        }
    }

    /** 抽出便于测试覆写注入假响应、不真连网。 */
    protected String send(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(props.getBaseUrl() + "/v1/agents/qa/invoke"))
            .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();
        HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new SmartLayerException("智能层返回非 2xx: " + resp.statusCode());
        }
        return resp.body();
    }
}
