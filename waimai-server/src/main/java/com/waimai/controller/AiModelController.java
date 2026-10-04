package com.waimai.controller;

import com.waimai.common.annotation.RequireRole;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.AiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理端：AI 模型池配置。支持多模型录入、启停、优先级排序与连通性测试。
 * 高优先级模型不可用时，AI 服务会自动降级到下一个已启用模型。
 */
@RestController
@RequestMapping("/api/admin/ai-models")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class AiModelController {

    private final AiModelService aiModelService;

    /** 模型列表（按优先级升序，API Key 脱敏） */
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(aiModelService.list());
    }

    /** 模型池运行状态：配置来源、当前实际生效模型、各模型最近结果与冷却剩余 */
    @GetMapping("/status")
    public R<Map<String, Object>> status() {
        return R.ok(aiModelService.status());
    }

    /** 新增或更新（id 为空即新增） */
    @PostMapping
    public R<Map<String, Object>> save(@RequestBody WebDTO.AiModelSaveReq req) {
        Long id = aiModelService.save(req);
        return R.ok(Map.<String, Object>of("id", id));
    }

    @DeleteMapping("/{id}")
    public R<Void> remove(@PathVariable Long id) {
        aiModelService.remove(id);
        return R.ok();
    }

    /** 启用 / 禁用 */
    @PutMapping("/{id}/enabled")
    public R<Void> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        aiModelService.toggle(id, enabled);
        return R.ok();
    }

    /** 优先级调整：direction = up / down / top */
    @PostMapping("/{id}/move")
    public R<Void> move(@PathVariable Long id, @RequestParam String direction) {
        aiModelService.move(id, direction);
        return R.ok();
    }

    /** 连通性测试（不落库，直接拿表单内容试一次） */
    @PostMapping("/test")
    public R<Map<String, Object>> test(@RequestBody WebDTO.AiModelSaveReq req) {
        return R.ok(aiModelService.test(req));
    }

    /**
     * 拉取该服务商当前可用的模型名。
     * 手填模型名很容易填错（例如把 zen 的模型名填到 DeepSeek 端点），这里直接向端点问一次。
     */
    @PostMapping("/catalog")
    public R<Map<String, Object>> catalog(@RequestBody WebDTO.AiModelSaveReq req) {
        return R.ok(aiModelService.catalog(req));
    }

    /** 按 id 测试：使用库中已保存的密钥（列表页脱敏展示，无法直接回传） */
    @PostMapping("/{id}/test")
    public R<Map<String, Object>> testById(@PathVariable Long id) {
        return R.ok(aiModelService.testById(id));
    }
}
