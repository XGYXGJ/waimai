package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 订单会话：一个订单一个会话，用户与商家在此沟通并提起售后工单。 */
@Data
@TableName("im_session")
public class ImSession {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long userId;

    private Long merchantId;

    /** OPEN / CLOSED */
    private String status;

    private String lastMsg;

    private LocalDateTime lastMsgAt;

    private Integer userUnread;

    private Integer merchantUnread;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
