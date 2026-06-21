package com.sellm.teaching;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** REST 调 Python /v1/agents/teaching/invoke。强制 HTTP/1.1。 */
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
    public String generate(String task, String iepContentOrPlan, String disorderType, String scene, String mode) {
        try {
            ObjectNode body = json.createObjectNode();
            body.put("task", task);
            if ("courseware".equals(task)) {
                body.put("lessonPlanContent", iepContentOrPlan);
            } else {
                body.put("iepContent", iepContentOrPlan);
            }
            body.put("disorderType", disorderType);
            body.put("scene", scene);
            body.put("mode", mode);
            String resp = send(json.writeValueAsString(body));
            JsonNode node = json.readTree(resp);
            return node.path("content").asText("");
        } catch (Exception e) {
            throw new SmartLayerException("智能层调用失败", e);
        }
    }

    protected String send(String requestBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(props.getBaseUrl() + "/v1/agents/teaching/invoke"))
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
