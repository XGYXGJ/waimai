package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.waimai.client.AiClient;
import com.waimai.common.exception.BizException;
import com.waimai.dto.WebDTO;
import com.waimai.entity.AiModel;
import com.waimai.mapper.AiModelMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI 模型池管理：增删改查、启停、优先级排序、连通性测试。
 * 配置存于 ai_model 表，AI 服务（FastAPI）按 priority 升序取用并在失败时逐级降级。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiModelService {

    private final AiModelMapper mapper;
    private final AiClient aiClient;

    /** 全部模型（按优先级升序），API Key 脱敏 */
    public List<Map<String, Object>> list() {
        return mapper.selectList(new QueryWrapper<AiModel>().orderByAsc("priority", "id"))
                .stream().map(this::mask).toList();
    }

    public Long save(WebDTO.AiModelSaveReq req) {
        validate(req);
        AiModel m = new AiModel();
        m.setId(req.getId());
        m.setName(req.getName().trim());
        m.setProvider(req.getProvider().trim());
        m.setBaseUrl(blankToNull(req.getBaseUrl()));
        m.setApiKey(blankToNull(req.getApiKey()));
        m.setModelId(req.getModelId().trim());
        m.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        m.setTimeout(req.getTimeout() == null || req.getTimeout() <= 0 ? 30 : req.getTimeout());
        m.setRemark(blankToNull(req.getRemark()));

        if (req.getId() == null) {
            // 新增：默认排到末尾
            Integer p = req.getPriority();
            if (p == null) {
                Integer max = maxPriority();
                p = (max == null ? 100 : max + 10);
            }
            m.setPriority(p);
            mapper.insert(m);
        } else {
            AiModel exist = mapper.selectById(req.getId());
            if (exist == null) throw new BizException("模型不存在");
            m.setPriority(req.getPriority() == null ? exist.getPriority() : req.getPriority());
            mapper.updateById(m);
        }
        notifyReload();
        return m.getId();
    }

    public void remove(Long id) {
        if (mapper.selectById(id) == null) throw new BizException("模型不存在");
        mapper.deleteById(id);
        notifyReload();
    }

    /** 启用 / 禁用 */
    public void toggle(Long id, Integer enabled) {
        AiModel m = mapper.selectById(id);
        if (m == null) throw new BizException("模型不存在");
        m.setEnabled(enabled == null || enabled == 1 ? 1 : 0);
        mapper.updateById(m);
        notifyReload();
    }

    /**
     * 调整优先级：up 上移 / down 下移 / top 置顶。
     * 采用与相邻项交换 priority 的方式，避免全表重排。
     */
    public void move(Long id, String direction) {
        AiModel cur = mapper.selectById(id);
        if (cur == null) throw new BizException("模型不存在");

        if ("top".equalsIgnoreCase(direction)) {
            Integer min = minPriority();
            if (min != null && !min.equals(cur.getPriority())) {
                cur.setPriority(min - 10);
                mapper.updateById(cur);
                notifyReload();
            }
            return;
        }

        boolean up = "up".equalsIgnoreCase(direction);
        AiModel neighbour = up
                ? mapper.selectOne(new QueryWrapper<AiModel>()
                        .lt("priority", cur.getPriority()).orderByDesc("priority").last("limit 1"))
                : mapper.selectOne(new QueryWrapper<AiModel>()
                        .gt("priority", cur.getPriority()).orderByAsc("priority").last("limit 1"));
        if (neighbour == null) return; // 已在顶端或底端

        int cp = cur.getPriority();
        cur.setPriority(neighbour.getPriority());
        neighbour.setPriority(cp);
        mapper.updateById(cur);
        mapper.updateById(neighbour);
        notifyReload();
    }

    /**
     * 模型池运行状态（透传 AI 服务）：配置来源、当前生效模型、各模型最近一次调用
     * 结果与冷却剩余时间。管理端据此展示「现在实际在用哪个模型」。
     */
    public Map<String, Object> status() {
        AiClient.AiResp resp = aiClient.modelsStatus();
        Map<String, Object> out = new LinkedHashMap<>();
        // 注意：这里必须判断「是否等于 200」。之前写成 httpStatus != 0，
        // 而正常响应就是 200（非 0），导致服务明明是通的也被判成失败，
        // 管理端顶部就会一直显示「配置来源：AI 服务不可达」，而同一页的「测试」却正常。
        if (resp.httpStatus != 200 || resp.data == null || !resp.data.isObject()) {
            out.put("ok", false);
            out.put("reachable", resp.httpStatus != 0);
            out.put("message", resp.httpStatus == 0
                    ? "AI 服务不可达（docker compose up -d ai）"
                    : "AI 服务返回 HTTP " + resp.httpStatus
                            + (resp.httpStatus == 404
                                    ? "：该实例没有 /ai/admin/* 接口，跑的是旧版 waimai-ai，"
                                            + "请执行 docker compose up -d --build ai"
                                    : ""));
            return out;
        }
        out.put("ok", true);
        out.put("reachable", true);
        out.put("source", text(resp.data, "source"));
        out.put("mysqlError", text(resp.data, "mysqlError"));

        JsonNode current = resp.data.get("current");
        if (current != null && current.isObject()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", text(current, "name"));
            c.put("provider", text(current, "provider"));
            c.put("modelId", text(current, "modelId"));
            out.put("current", c);
        } else {
            // 还没成功调用过任何模型
            out.put("current", null);
        }

        List<Map<String, Object>> models = new ArrayList<>();
        JsonNode ms = resp.data.get("models");
        if (ms != null && ms.isObject()) {
            ms.fields().forEachRemaining(entry -> {
                JsonNode v = entry.getValue();
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("key", entry.getKey());
                m.put("name", text(v, "name"));
                m.put("provider", text(v, "provider"));
                m.put("modelId", text(v, "modelId"));
                m.put("ok", v.path("ok").asBoolean(false));
                m.put("error", text(v, "error"));
                m.put("cooldownSec", v.path("cooldownSec").asInt(0));
                m.put("fails", v.path("fails").asInt(0));
                models.add(m);
            });
        }
        out.put("models", models);
        return out;
    }

    /** 配置变更后通知 AI 服务清缓存与失败冷却，让改动立即生效（失败不影响主流程） */
    private void notifyReload() {
        AiClient.AiResp resp = aiClient.reloadModels();
        if (resp.httpStatus != 0 && resp.httpStatus != 200) {
            log.warn("通知 AI 服务刷新模型池失败：HTTP {}", resp.httpStatus);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node == null ? null : node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** 连通性测试：转发给 AI 服务实际发一次请求 */
    public Map<String, Object> test(WebDTO.AiModelSaveReq req) {
        validate(req);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", req.getName());
        payload.put("provider", req.getProvider());
        payload.put("baseUrl", req.getBaseUrl());
        payload.put("apiKey", resolveApiKey(req));
        payload.put("modelId", req.getModelId());
        payload.put("timeout", req.getTimeout() == null ? 30 : req.getTimeout());

        Map<String, Object> out = new LinkedHashMap<>();
        long t0 = System.currentTimeMillis();
        AiClient.AiResp resp = aiClient.testModel(payload);
        boolean ok = resp.data != null && resp.data.has("ok") && resp.data.get("ok").asBoolean();
        out.put("ok", ok);
        out.put("latencyMs", System.currentTimeMillis() - t0);
        if (ok && resp.data.has("message")) {
            out.put("message", resp.data.get("message").asText());
            return out;
        }
        // 失败时给出可定位的原因，而不是笼统的「AI 服务不可达」
        String err = resp.data != null && resp.data.has("error") ? resp.data.get("error").asText() : "";
        String msg;
        if (resp.httpStatus == 0) {
            msg = "连不上 AI 服务（" + err + "）。请先启动 waimai-ai："
                    + "docker compose up -d ai，或本地 python -m uvicorn app.main:app --port 8000";
        } else if (resp.httpStatus == 401 || resp.httpStatus == 403) {
            msg = "AI 服务返回 " + resp.httpStatus + "：内部 Token 不匹配。"
                    + "请让后端 waimai.ai.internal-token 与 AI 服务环境变量 INTERNAL_TOKEN 一致（默认均为 dev-token-123）";
        } else if (resp.httpStatus == 404) {
            msg = "AI 服务返回 404：该实例上没有 /ai/admin/* 接口，说明跑的是旧版 waimai-ai"
                    + "（多为 Docker 容器仍是旧镜像）。请执行：docker compose up -d --build ai";
        } else if (resp.httpStatus > 0) {
            msg = "AI 服务返回 HTTP " + resp.httpStatus + "：" + err;
        } else if (!ok && resp.data != null && resp.data.has("message")) {
            msg = resp.data.get("message").asText();
        } else {
            msg = "AI 服务不可达：" + err;
        }
        out.put("message", msg);
        return out;
    }

    /** 拉取该服务商当前可用的模型名列表（zen 免费池会轮换，OpenAI 端点只认自己有的名字） */
    public Map<String, Object> catalog(WebDTO.AiModelSaveReq req) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", req == null ? "zen" : blankToNull(req.getProvider()));
        payload.put("baseUrl", req == null ? null : blankToNull(req.getBaseUrl()));
        payload.put("apiKey", req == null ? null : blankToNull(req.getApiKey()));

        Map<String, Object> out = new LinkedHashMap<>();
        AiClient.AiResp resp = aiClient.modelCatalog(payload);
        boolean ok = resp.data != null && resp.data.has("ok") && resp.data.get("ok").asBoolean();
        out.put("ok", ok);
        out.put("models", new ArrayList<>());
        if (resp.data != null && resp.data.has("models")) {
            List<Map<String, Object>> models = new ArrayList<>();
            for (JsonNode n : resp.data.get("models")) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", n.path("id").asText());
                m.put("free", n.path("free").asBoolean());
                models.add(m);
            }
            out.put("models", models);
        }
        String msg = resp.data != null && resp.data.has("message") ? resp.data.get("message").asText() : "";
        if (!ok && msg.isEmpty()) {
            if (resp.httpStatus == 0) {
                msg = "连不上 AI 服务，请先启动 waimai-ai（docker compose up -d ai）";
            } else if (resp.httpStatus == 404) {
                msg = "AI 服务返回 404：该实例上没有 /ai/admin/* 接口，说明跑的是旧版 waimai-ai。"
                        + "请执行：docker compose up -d --build ai";
            } else {
                msg = "AI 服务返回 HTTP " + resp.httpStatus;
            }
        }
        out.put("message", msg);
        return out;
    }

    /** 按 id 取库中真实配置做连通性测试（列表页 apiKey 已脱敏，需从库里取） */
    public Map<String, Object> testById(Long id) {
        AiModel m = mapper.selectById(id);
        if (m == null) throw new BizException("模型不存在");
        WebDTO.AiModelSaveReq req = new WebDTO.AiModelSaveReq();
        req.setId(m.getId());
        req.setName(m.getName());
        req.setProvider(m.getProvider());
        req.setBaseUrl(m.getBaseUrl());
        req.setApiKey(m.getApiKey());
        req.setModelId(m.getModelId());
        req.setTimeout(m.getTimeout());
        return test(req);
    }

    private void validate(WebDTO.AiModelSaveReq req) {
        if (req == null) throw new BizException("参数为空");
        if (isBlank(req.getName())) throw new BizException("请填写模型名称");
        if (isBlank(req.getProvider())) throw new BizException("请选择服务商类型");
        if (isBlank(req.getModelId())) throw new BizException("请填写模型标识");
        String p = req.getProvider() == null ? "" : req.getProvider().trim();
        if (!Set.of("zen", "openai", "ollama").contains(p)) {
            throw new BizException("服务商类型仅支持 zen/openai/ollama");
        }
        if ("openai".equals(p) && isBlank(req.getBaseUrl())) {
            throw new BizException("OpenAI 兼容模型需填写 API 地址");
        }
        if ("ollama".equals(p) && isBlank(req.getBaseUrl())) {
            throw new BizException("Ollama 需填写服务地址");
        }
    }

    private Integer maxPriority() {
        AiModel m = mapper.selectOne(new QueryWrapper<AiModel>()
                .orderByDesc("priority").last("limit 1"));
        return m == null ? null : m.getPriority();
    }

    private Integer minPriority() {
        AiModel m = mapper.selectOne(new QueryWrapper<AiModel>()
                .orderByAsc("priority").last("limit 1"));
        return m == null ? null : m.getPriority();
    }

    private Map<String, Object> mask(AiModel m) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("id", m.getId());
        r.put("name", m.getName());
        r.put("provider", m.getProvider());
        r.put("baseUrl", m.getBaseUrl());
        r.put("apiKey", m.getApiKey() == null || m.getApiKey().isEmpty() ? "" : "******");
        r.put("hasKey", m.getApiKey() != null && !m.getApiKey().isEmpty());
        r.put("modelId", m.getModelId());
        r.put("enabled", m.getEnabled());
        r.put("priority", m.getPriority());
        r.put("timeout", m.getTimeout());
        r.put("remark", m.getRemark());
        return r;
    }

    /**
     * 测试时用的密钥：表单留空表示「沿用已保存的密钥」。
     * 列表接口返回的 apiKey 是脱敏值，管理端编辑弹窗里也拿不到明文，
     * 若直接透传空值，编辑一个配好 Key 的模型点「测试连接」必然报「未填写 API Key」。
     */
    private String resolveApiKey(WebDTO.AiModelSaveReq req) {
        if (!isBlank(req.getApiKey())) return req.getApiKey().trim();
        if (req.getId() == null) return null;
        AiModel exist = mapper.selectById(req.getId());
        return exist == null ? null : exist.getApiKey();
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
