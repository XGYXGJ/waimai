package com.waimai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理端关键操作留痕：审核（商户/骑手/竞价）、退款、封禁、删除违规评价。
 * 对应设计文档第 9 章「关键操作写操作日志」。
 */
@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人（user.id，角色 ADMIN） */
    private Long adminId;

    /** 动作码：AUDIT_MERCHANT / AUDIT_RIDER / CHANGE_USER_STATUS / REFUND_ORDER / DELETE_REVIEW / AUDIT_CAMPAIGN */
    private String action;

    /** 目标类型：MERCHANT / RIDER / USER / ORDER / REVIEW / BID_CAMPAIGN */
    private String targetType;

    private Long targetId;

    private String detail;

    private String ip;

    private LocalDateTime createdAt;
}
