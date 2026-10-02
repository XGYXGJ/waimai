package com.waimai.controller;

import com.waimai.common.result.R;
import com.waimai.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {

    private final SysConfigService sysConfigService;

    @Value("${waimai.upload.dir:./uploads}")
    private String uploadDir;

    @GetMapping("/health")
    public R<String> health() {
        return R.ok("ok");
    }

    /** 地图 Key（前端渲染用，用户端/骑手端/商户端调用）。JS API 2.0 需配套安全密钥。 */
    @GetMapping("/config/map")
    public R<Map<String, Object>> mapConfig() {
        String jsKey = sysConfigService.get("map.js_key");
        String securityCode = sysConfigService.get("map.security_code");
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("jsKey", jsKey == null ? "" : jsKey);
        m.put("securityCode", securityCode == null ? "" : securityCode);
        return R.ok(m);
    }

    /** 图片上传：存本地 uploads 目录，返回可访问 URL */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return R.fail(400, "文件为空");
            }
            String original = file.getOriginalFilename();
            String ext = "";
            if (original != null && original.contains(".")) {
                ext = original.substring(original.lastIndexOf("."));
            }
            String filename = UUID.randomUUID().toString().replace("-", "") + ext;
            File dir = new File(uploadDir);
            if (!dir.exists()) dir.mkdirs();
            File dest = new File(dir, filename);
            file.transferTo(dest.getAbsoluteFile());
            return R.ok(Map.of("url", "/uploads/" + filename));
        } catch (Exception e) {
            return R.fail(500, "上传失败: " + e.getMessage());
        }
    }
}
