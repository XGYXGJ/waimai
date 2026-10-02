package com.waimai.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AI 服务（FastAPI）客户端。云端 → Ollama 两级降级由 AI 服务内部完成，
 * 后端只关心最终结果与 degraded 标记；AI 服务整体不可达时，本客户端返回 degraded。
 */
@Component
@RequiredArgsConstructor
public class AiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper om = new ObjectMapper();

    @Value("${waimai.ai.base-url:http://waimai-ai:8000}")
    private String baseUrl;

    @Value("${waimai.ai.internal-token:internal-secret}")
    private String internalToken;

    /** 智能客服 */
    public AiResp chat(Map<String, Object> payload) {
        return post("/ai/chat", payload);
    }

    /** 每日推荐理由 */
    public AiResp recommend(Map<String, Object> payload) {
        return post("/ai/recommend", payload);
    }

    /** 情感分析 */
    public AiResp sentiment(String reviewId, String content) {
        return post("/ai/sentiment", Map.of("reviewId", reviewId, "content", content));
    }

    /** 销量预测 */
    public AiResp forecast(Map<String, Object> payload) {
        return post("/ai/forecast", payload);
    }

    private AiResp post(String path, Map<String, Object> body) {
        AiResp resp = new AiResp();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", internalToken);
            HttpEntity<String> entity = new HttpEntity<>(om.writeValueAsString(body), headers);
            ResponseEntity<String> re = restTemplate.exchange(
                    baseUrl + path, HttpMethod.POST, entity, String.class);
            JsonNode node = om.readTree(re.getBody());
            resp.data = node;
            resp.degraded = node.has("degraded") && node.get("degraded").asBoolean();
        } catch (Exception e) {
            resp.degraded = true;
            resp.data = om.createObjectNode().put("degraded", true).put("error", e.getMessage());
        }
        return resp;
    }

    public static class AiResp {
        public JsonNode data;
        public boolean degraded;

        public String str(String field) {
            return data != null && data.has(field) ? data.get(field).asText() : null;
        }
    }
}
