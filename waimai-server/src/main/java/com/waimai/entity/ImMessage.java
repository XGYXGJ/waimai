package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 会话消息：文本 / 图片 / 订单卡片 / 工单卡片。 */
@Data
@TableName("im_message")
public class ImMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    /** USER / MERCHANT / SYSTEM */
    private String senderRole;

    /** TEXT / IMAGE / ORDER / TICKET */
    private String msgType;

    private String content;

    /** JSON 字符串：图片数组 / 订单快照 / 工单信息 */
    private String payload;

    private LocalDateTime createdAt;
}
