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

    /** 模型连通性测试（配置不落库，仅试跑一次） */
    public AiResp testModel(Map<String, Object> payload) {
        return post("/ai/admin/test-model", payload);
    }

    /** 拉取某服务商当前可用的模型名列表（避免手填模型名出错） */
    public AiResp modelCatalog(Map<String, Object> payload) {
        return post("/ai/admin/models/catalog", payload);
    }

    /** 模型池运行状态：配置来源、当前生效模型、各模型最近一次结果与冷却剩余时间 */
    public AiResp modelsStatus() {
        return exchange(HttpMethod.GET, "/ai/admin/models/status", null);
    }

    /** 管理端改过配置后通知 AI 服务清缓存与失败冷却，让改动立即生效 */
    public AiResp reloadModels() {
        return exchange(HttpMethod.POST, "/ai/admin/models/reload", Map.of());
    }

    private AiResp post(String path, Map<String, Object> body) {
        return exchange(HttpMethod.POST, path, body);
    }

    private AiResp exchange(HttpMethod method, String path, Map<String, Object> body) {
        AiResp resp = new AiResp();
        try {
            HttpHeaders headers = new HttpHeaders();
            if (body != null) headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Token", internalToken);
            HttpEntity<String> entity = new HttpEntity<>(
                    body == null ? null : om.writeValueAsString(body), headers);
            ResponseEntity<String> re = restTemplate.exchange(
                    baseUrl + path, method, entity, String.class);
            // 必须显式记录状态码：httpStatus 的默认值是 -1，成功分支以前没赋值，
            // 调用方看到的就是 -1，于是「服务明明通了」也被当成异常状态
            // （管理端顶部会显示「配置来源：未知 / AI 服务返回 HTTP -1」）。
            resp.httpStatus = re.getStatusCode().value();
            String respBody = re.getBody();
            if (respBody == null || respBody.isBlank()) {
                resp.data = om.createObjectNode();
            } else {
                JsonNode node = om.readTree(respBody);
                resp.data = node;
                resp.degraded = node.has("degraded") && node.get("degraded").asBoolean();
            }
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // 4xx/5xx：RestTemplate 默认抛异常，这里转成可读的原因，避免前端只看到「AI 服务不可达」
            resp.degraded = true;
            resp.httpStatus = e.getStatusCode().value();
            String errBody = e.getResponseBodyAsString();
            String detail = errBody == null || errBody.isBlank() ? e.getStatusText() : errBody;
            if (detail.length() > 200) detail = detail.substring(0, 200);
            resp.data = om.createObjectNode()
                    .put("degraded", true)
                    .put("httpStatus", resp.httpStatus)
                    .put("error", "HTTP " + resp.httpStatus + " " + detail);
        } catch (Exception e) {
            // 连接被拒 / 超时 / DNS 失败：AI 服务根本没起来
            resp.degraded = true;
            resp.httpStatus = 0;
            resp.data = om.createObjectNode()
                    .put("degraded", true)
                    .put("httpStatus", 0)
                    .put("error", e.getMessage());
        }
        return resp;
    }

    public static class AiResp {
        public JsonNode data;
        public boolean degraded;
        /** 0 表示连不上；其它为真实 HTTP 状态码 */
        public int httpStatus = -1;

        public String str(String field) {
            return data != null && data.has(field) ? data.get(field).asText() : null;
        }
    }
}
