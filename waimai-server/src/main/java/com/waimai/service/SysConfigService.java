package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.entity.SysConfig;
import com.waimai.mapper.SysConfigMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统配置：本地缓存 + DB。管理端修改后立即生效（无需重启）。
 * LLM API Key 与高德地图 Key 均在此配置（管理端「系统设置」页面）。
 */
@Service
public class SysConfigService {

    private final SysConfigMapper mapper;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SysConfigService(SysConfigMapper mapper) {
        this.mapper = mapper;
    }

    public String get(String key) {
        return cache.computeIfAbsent(key, k -> {
            SysConfig c = mapper.selectById(k);
            return c == null ? "" : c.getConfigValue();
        });
    }

    public double getDouble(String key, double def) {
        try {
            return Double.parseDouble(get(key));
        } catch (Exception e) {
            return def;
        }
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key));
        } catch (Exception e) {
            return def;
        }
    }

    public void set(String key, String value) {
        SysConfig c = new SysConfig();
        c.setConfigKey(key);
        c.setConfigValue(value);
        SysConfig exist = mapper.selectById(key);
        if (exist == null) {
            mapper.insert(c);
        } else {
            mapper.updateById(c);
        }
        cache.put(key, value);
    }

    public List<SysConfig> listAll() {
        return mapper.selectList(null);
    }

    /** 管理端展示：API Key 类配置脱敏 */
    public List<Map<String, Object>> listAllMasked() {
        return mapper.selectList(null).stream().map(c -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("configKey", c.getConfigKey());
            String v = c.getConfigValue();
            boolean secret = c.getConfigKey().contains("api_key");
            m.put("configValue", secret && v != null && !v.isEmpty() ? "******" : v);
            m.put("description", c.getDescription());
            return m;
        }).toList();
    }
}
