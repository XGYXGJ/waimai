package com.waimai.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 售后工单：退款 / 赔偿 / 补发 / 其他，由用户在会话中发起，商家处理。 */
@Data
@TableName("im_ticket")
public class ImTicket {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long orderId;

    private Long userId;

    private Long merchantId;

    /** REFUND / COMPENSATE / REISSUE / OTHER */
    private String type;

    private BigDecimal amount;

    private String reason;

    /** JSON 字符串：凭证图片 URL 数组 */
    private String images;

    /** PENDING / PROCESSING / APPROVED / REJECTED / CLOSED */
    private String status;

    private String merchantReply;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
