package com.waimai.controller;

import com.waimai.common.annotation.RequireRole;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/config")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class ConfigController {

    private final SysConfigService sysConfigService;

    /** 全部配置（API Key 脱敏） */
    @GetMapping
    public R<List<Map<String, Object>>> list() {
        return R.ok(sysConfigService.listAllMasked());
    }

    /** 修改配置（LLM API Key / 地图 Key / 推荐权重等） */
    @PutMapping("/{key}")
    public R<Void> update(@PathVariable String key, @RequestBody WebDTO.ConfigUpdateReq req) {
        sysConfigService.set(key, req.getConfigValue());
        return R.ok();
    }
}
