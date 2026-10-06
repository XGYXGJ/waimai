package com.waimai.entity;

import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 订单会话：一个订单一个会话，用户 + 商家 + 骑手三方在此沟通并提起售后工单。 */
@Data
@TableName("im_session")
public class ImSession {
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long userId;

    private Long merchantId;

    /** 接单骑手（orders.rider_id）；订单未被接单时为 null，骑手接入后回填 */
    private Long riderId;

    /** OPEN / CLOSED */
    private String status;

    /** 会话失效时间：订单送达后 30 分钟；送达前为 null（表示随订单状态判断） */
    private java.time.LocalDateTime closeAt;

    private String lastMsg;

    private LocalDateTime lastMsgAt;

    private Integer userUnread;

    private Integer merchantUnread;

    private Integer riderUnread;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
