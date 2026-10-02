package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("chat_message")
public class ChatMessage {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private String role;

    private String content;

    private LocalDateTime createdAt;
}