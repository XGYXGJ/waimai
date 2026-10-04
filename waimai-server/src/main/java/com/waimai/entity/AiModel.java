package com.waimai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 模型池条目。provider: zen(OpenCode 免费模型,免密钥) / openai(OpenAI 兼容) / ollama(本地)。
 * priority 越小越优先，高优先级不可用时自动降级到下一个。
 */
@Data
@TableName("ai_model")
public class AiModel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String provider;

    private String baseUrl;

    private String apiKey;

    private String modelId;

    /** 1 启用 / 0 禁用 */
    private Integer enabled;

    private Integer priority;

    private Integer timeout;

    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
