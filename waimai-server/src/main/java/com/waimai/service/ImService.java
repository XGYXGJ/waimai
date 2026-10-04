package com.waimai.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.waimai.common.exception.BizException;
import com.waimai.dto.WebDTO;
import com.waimai.entity.ImMessage;
import com.waimai.entity.ImSession;
import com.waimai.entity.ImTicket;
import com.waimai.entity.Merchant;
import com.waimai.entity.Orders;
import com.waimai.entity.OrderItem;
import com.waimai.mapper.ImMessageMapper;
import com.waimai.mapper.ImSessionMapper;
import com.waimai.mapper.ImTicketMapper;
import com.waimai.mapper.MerchantMapper;
import com.waimai.mapper.OrderItemMapper;
import com.waimai.mapper.OrdersMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 订单会话（用户 ↔ 商家）与售后工单。
 *
 * 会话以订单为单位（im_session.order_id 唯一），会话内消息分四类：
 * TEXT 文本 / IMAGE 图片 / ORDER 订单卡片 / TICKET 工单卡片。
 * 工单由用户在会话内发起（退款、赔偿、补发、其他），商家在商户端处理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImService {

    private final ImSessionMapper sessionMapper;
    private final ImMessageMapper messageMapper;
    private final ImTicketMapper ticketMapper;
    private final OrdersMapper ordersMapper;
    private final OrderItemMapper orderItemMapper;
    private final MerchantMapper merchantMapper;
    private final NotificationService notificationService;

    private final ObjectMapper om = new ObjectMapper();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> MSG_TYPES = Set.of("TEXT", "IMAGE", "ORDER", "TICKET");
    private static final Set<String> TICKET_TYPES = Set.of("REFUND", "COMPENSATE", "REISSUE", "OTHER");
    private static final Set<String> TICKET_ACTIONS = Set.of("PROCESSING", "APPROVED", "REJECTED", "CLOSED");

    private static final Map<String, String> TICKET_TYPE_TEXT = Map.of(
            "REFUND", "退款", "COMPENSATE", "赔偿", "REISSUE", "补发", "OTHER", "其他");
    private static final Map<String, String> TICKET_STATUS_TEXT = Map.of(
            "PENDING", "待处理", "PROCESSING", "处理中", "APPROVED", "已同意", "REJECTED", "已驳回", "CLOSED", "已关闭");

    /* ==================== 用户端 ==================== */

    /** 按订单开启会话（幂等：已有则直接返回） */
    public Map<String, Object> openSession(Long userId, Long orderId) {
        if (orderId == null) throw new BizException("订单 ID 不能为空");
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (!Objects.equals(order.getUserId(), userId)) throw new BizException("无权访问该订单");
        return sessionVo(getOrCreate(order), true);
    }

    public List<Map<String, Object>> mySessions(Long userId) {
        return sessionMapper.selectList(new QueryWrapper<ImSession>()
                        .eq("user_id", userId).orderByDesc("updated_at"))
                .stream().map(s -> sessionVo(s, true)).collect(Collectors.toList());
    }

    /**
     * 拉取消息。sinceId 用于增量轮询；进入会话时顺带把本侧未读清零。
     * operatorId：用户端传 userId，商户端传 merchantId，用于越权校验。
     */
    public Map<String, Object> messages(Long sessionId, Long sinceId, boolean userSide, Long operatorId) {
        ImSession s = requireSession(sessionId);
        checkOwner(s, userSide, operatorId);
        QueryWrapper<ImMessage> q = new QueryWrapper<ImMessage>().eq("session_id", s.getId());
        if (sinceId != null && sinceId > 0) q.gt("id", sinceId);
        q.orderByAsc("id").last("limit 500");
        List<ImMessage> list = messageMapper.selectList(q);

        if (userSide) {
            clearUnread(s, "user");
        } else {
            clearUnread(s, "merchant");
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", sessionVo(s, userSide));
        out.put("records", list.stream().map(this::messageVo).collect(Collectors.toList()));
        out.put("lastId", list.isEmpty() ? (sinceId == null ? 0L : sinceId)
                : list.get(list.size() - 1).getId());
        return out;
    }

    /** 发消息：role = USER / MERCHANT；operatorId 用于越权校验 */
    public Map<String, Object> send(Long sessionId, String role, Long operatorId, WebDTO.ImSendReq req) {
        ImSession s = requireSession(sessionId);
        checkOwner(s, "USER".equals(role), operatorId);
        String type = req.getMsgType() == null || req.getMsgType().isBlank()
                ? "TEXT" : req.getMsgType().trim().toUpperCase();
        if (!MSG_TYPES.contains(type)) throw new BizException("不支持的消息类型：" + type);

        Map<String, Object> payload = null;
        String content = req.getContent() == null ? "" : req.getContent().trim();

        if ("IMAGE".equals(type)) {
            List<String> images = req.getImages() == null ? List.of()
                    : req.getImages().stream().filter(Objects::nonNull)
                            .filter(x -> !x.isBlank()).collect(Collectors.toList());
            if (images.isEmpty()) throw new BizException("请先选择图片");
            payload = Map.of("images", images);
            if (content.isEmpty()) content = "[图片]";
        } else if ("ORDER".equals(type)) {
            payload = orderSnapshot(s.getOrderId());
            if (content.isEmpty()) content = "[订单]";
        } else {
            if (content.isEmpty()) throw new BizException("消息内容不能为空");
            if (content.length() > 1000) content = content.substring(0, 1000);
        }

        if ("CLOSED".equals(s.getStatus())) {
            s.setStatus("OPEN");
            sessionMapper.updateById(s);
        }

        ImMessage m = insertMessage(s, role, type, content, payload);
        touchSession(s, content);
        incrUnread(s, role);

        // 对方不在会话页时给一条站内通知（用户发 → 通知商家；商家发 → 通知用户）
        try {
            if ("USER".equals(role)) {
                Long merchantUserId = merchantUserId(s.getMerchantId());
                if (merchantUserId != null) {
                    notificationService.notify(merchantUserId, "IM", "新的顾客消息", content);
                }
            } else {
                notificationService.notify(s.getUserId(), "IM", "商家回复了你", content);
            }
        } catch (Exception e) {
            log.warn("im notify failed: {}", e.getMessage());
        }
        return messageVo(m);
    }

    /** 用户在会话内发起售后工单 */
    public Map<String, Object> createTicket(Long userId, Long sessionId, WebDTO.ImTicketReq req) {
        ImSession s = requireSession(sessionId);
        if (!Objects.equals(s.getUserId(), userId)) throw new BizException("无权操作该会话");
        if (req == null) throw new BizException("参数为空");

        String type = req.getType() == null ? "OTHER" : req.getType().trim().toUpperCase();
        if (!TICKET_TYPES.contains(type)) throw new BizException("工单类型不支持：" + type);
        String reason = req.getReason() == null ? "" : req.getReason().trim();
        if (reason.isEmpty()) throw new BizException("请填写申请原因");
        if (reason.length() > 500) reason = reason.substring(0, 500);
        BigDecimal amount = req.getAmount() == null ? BigDecimal.ZERO : req.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) < 0) throw new BizException("金额不能为负");
        if (amount.compareTo(new BigDecimal("999999")) > 0) throw new BizException("金额超出范围");

        List<String> images = req.getImages() == null ? List.of()
                : req.getImages().stream().filter(Objects::nonNull)
                        .filter(x -> !x.isBlank()).collect(Collectors.toList());

        ImTicket t = new ImTicket();
        t.setSessionId(s.getId());
        t.setOrderId(s.getOrderId());
        t.setUserId(userId);
        t.setMerchantId(s.getMerchantId());
        t.setType(type);
        t.setAmount(amount);
        t.setReason(reason);
        t.setImages(toJson(images));
        t.setStatus("PENDING");
        ticketMapper.insert(t);

        // 会话内同步展示一张工单卡片
        Map<String, Object> payload = ticketPayload(t, images);
        ImMessage m = insertMessage(s, "USER", "TICKET",
                "[工单] " + TICKET_TYPE_TEXT.getOrDefault(type, type) + " ¥" + amount, payload);
        touchSession(s, m.getContent());
        incrUnread(s, "USER");

        try {
            Long merchantUserId = merchantUserId(s.getMerchantId());
            if (merchantUserId != null) {
                notificationService.notify(merchantUserId, "TICKET", "新的售后工单",
                        TICKET_TYPE_TEXT.getOrDefault(type, type) + " ¥" + amount + "：" + reason);
            }
        } catch (Exception e) {
            log.warn("ticket notify failed: {}", e.getMessage());
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ticket", ticketVo(t));
        out.put("message", messageVo(m));
        return out;
    }

    public List<Map<String, Object>> myTickets(Long userId) {
        return ticketMapper.selectList(new QueryWrapper<ImTicket>()
                        .eq("user_id", userId).orderByDesc("created_at"))
                .stream().map(this::ticketVo).collect(Collectors.toList());
    }

    /** 工单详情：只允许工单所属用户查看（原先缺少归属校验，可枚举 id 越权读取） */
    public Map<String, Object> ticketDetail(Long ticketId, Long userId) {
        ImTicket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new BizException("工单不存在");
        if (!Objects.equals(t.getUserId(), userId)) throw new BizException("无权查看该工单");
        return ticketVo(t);
    }

    /* ==================== 商户端 ==================== */

    /** 由登录用户 ID 解析其商家 ID */
    public Long merchantIdOf(Long userId) {
        Merchant m = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("user_id", userId));
        if (m == null) throw new BizException("当前账号未绑定商家");
        return m.getId();
    }

    public List<Map<String, Object>> merchantSessions(Long userId) {
        Long merchantId = merchantIdOf(userId);
        return sessionMapper.selectList(new QueryWrapper<ImSession>()
                        .eq("merchant_id", merchantId).orderByDesc("updated_at"))
                .stream().map(s -> sessionVo(s, false)).collect(Collectors.toList());
    }

    public List<Map<String, Object>> merchantTickets(Long userId, String status) {
        Long merchantId = merchantIdOf(userId);
        QueryWrapper<ImTicket> q = new QueryWrapper<ImTicket>()
                .eq("merchant_id", merchantId);
        if (status != null && !status.isBlank()) q.eq("status", status.trim().toUpperCase());
        q.orderByDesc("created_at");
        return ticketMapper.selectList(q).stream().map(this::ticketVo).collect(Collectors.toList());
    }

    /** 商家处理工单：处理中 / 同意 / 驳回 / 关闭 */
    public Map<String, Object> handleTicket(Long userId, Long ticketId, WebDTO.ImTicketHandleReq req) {
        ImTicket t = ticketMapper.selectById(ticketId);
        if (t == null) throw new BizException("工单不存在");
        Long merchantId = merchantIdOf(userId);
        if (!Objects.equals(t.getMerchantId(), merchantId)) throw new BizException("无权处理该工单");

        String action = req == null || req.getAction() == null ? "" : req.getAction().trim().toUpperCase();
        if (!TICKET_ACTIONS.contains(action)) throw new BizException("处理动作不支持：" + action);
        String reply = req.getReply() == null ? "" : req.getReply().trim();
        if (reply.length() > 500) reply = reply.substring(0, 500);
        if (("APPROVED".equals(action) || "REJECTED".equals(action)) && reply.isEmpty()) {
            throw new BizException("请填写处理意见");
        }

        t.setStatus(action);
        t.setMerchantReply(reply.isEmpty() ? null : reply);
        ticketMapper.updateById(t);

        // 结果同步到会话，用户能直接看到
        ImSession s = sessionMapper.selectById(t.getSessionId());
        if (s != null) {
            Map<String, Object> payload = ticketPayload(t, fromJsonList(t.getImages()));
            ImMessage m = insertMessage(s, "SYSTEM", "TICKET",
                    "[工单结果] " + TICKET_STATUS_TEXT.getOrDefault(action, action)
                            + (reply.isEmpty() ? "" : "：" + reply), payload);
            touchSession(s, m.getContent());
            incrUnread(s, "MERCHANT");
            try {
                notificationService.notify(t.getUserId(), "TICKET", "售后工单有新的处理进展",
                        TICKET_STATUS_TEXT.getOrDefault(action, action)
                                + (reply.isEmpty() ? "" : "：" + reply));
            } catch (Exception e) {
                log.warn("ticket result notify failed: {}", e.getMessage());
            }
        }
        return ticketVo(t);
    }

    /* ==================== 内部方法 ==================== */

    private ImSession getOrCreate(Orders order) {
        ImSession s = sessionMapper.selectOne(new QueryWrapper<ImSession>()
                .eq("order_id", order.getId()));
        if (s != null) return s;

        s = new ImSession();
        s.setOrderId(order.getId());
        s.setUserId(order.getUserId());
        s.setMerchantId(order.getMerchantId());
        s.setStatus("OPEN");
        s.setUserUnread(0);
        s.setMerchantUnread(0);
        sessionMapper.insert(s);

        // 开会话时自动带出一张订单卡片，双方都不用重复描述订单
        insertMessage(s, "SYSTEM", "ORDER", "[订单]", orderSnapshot(order.getId()));
        touchSession(s, "[订单]");
        return s;
    }

    /** 越权校验：用户端比对 user_id，商户端比对 merchant_id */
    private void checkOwner(ImSession s, boolean userSide, Long operatorId) {
        if (operatorId == null) throw new BizException("未登录");
        Long expect = userSide ? s.getUserId() : s.getMerchantId();
        if (!Objects.equals(expect, operatorId)) throw new BizException("无权访问该会话");
    }

    private ImSession requireSession(Long sessionId) {
        if (sessionId == null) throw new BizException("会话 ID 不能为空");
        ImSession s = sessionMapper.selectById(sessionId);
        if (s == null) throw new BizException("会话不存在");
        return s;
    }

    private ImMessage insertMessage(ImSession s, String role, String type, String content,
                                    Map<String, Object> payload) {
        ImMessage m = new ImMessage();
        m.setSessionId(s.getId());
        m.setSenderRole(role);
        m.setMsgType(type);
        m.setContent(content);
        m.setPayload(payload == null ? null : toJson(payload));
        messageMapper.insert(m);
        return m;
    }

    private void touchSession(ImSession s, String preview) {
        s.setLastMsg(preview == null ? "" : (preview.length() > 100 ? preview.substring(0, 100) : preview));
        s.setLastMsgAt(LocalDateTime.now());
        sessionMapper.updateById(s);
    }

    /** 发件方的对侧未读 +1 */
    private void incrUnread(ImSession s, String senderRole) {
        if ("USER".equals(senderRole)) {
            s.setMerchantUnread((s.getMerchantUnread() == null ? 0 : s.getMerchantUnread()) + 1);
        } else {
            s.setUserUnread((s.getUserUnread() == null ? 0 : s.getUserUnread()) + 1);
        }
        sessionMapper.updateById(s);
    }

    private void clearUnread(ImSession s, String side) {
        boolean dirty = false;
        if ("user".equals(side) && s.getUserUnread() != null && s.getUserUnread() > 0) {
            s.setUserUnread(0);
            dirty = true;
        }
        if ("merchant".equals(side) && s.getMerchantUnread() != null && s.getMerchantUnread() > 0) {
            s.setMerchantUnread(0);
            dirty = true;
        }
        if (dirty) sessionMapper.updateById(s);
    }

    /** 订单快照：订单号、金额、状态、菜品摘要 */
    private Map<String, Object> orderSnapshot(Long orderId) {
        Orders o = ordersMapper.selectById(orderId);
        Map<String, Object> snap = new LinkedHashMap<>();
        if (o == null) return snap;
        snap.put("orderId", o.getId());
        snap.put("orderNo", o.getOrderNo());
        snap.put("status", o.getStatus());
        snap.put("payAmount", o.getPayAmount());
        snap.put("createdAt", o.getCreatedAt() == null ? null : FMT.format(o.getCreatedAt()));
        Merchant merchant = merchantMapper.selectById(o.getMerchantId());
        if (merchant != null) {
            snap.put("merchantId", merchant.getId());
            snap.put("merchantName", merchant.getShopName());
            snap.put("merchantLogo", merchant.getLogo());
        }
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", o.getId()));
        snap.put("items", items.stream().map(it -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("dishName", it.getDishName());
            m.put("quantity", it.getQuantity());
            m.put("price", it.getPrice());
            m.put("image", it.getImage());
            return m;
        }).collect(Collectors.toList()));
        return snap;
    }

    private Map<String, Object> ticketPayload(ImTicket t, List<String> images) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("ticketId", t.getId());
        p.put("type", t.getType());
        p.put("typeText", TICKET_TYPE_TEXT.getOrDefault(t.getType(), t.getType()));
        p.put("amount", t.getAmount());
        p.put("reason", t.getReason());
        p.put("images", images == null ? List.of() : images);
        p.put("status", t.getStatus());
        p.put("statusText", TICKET_STATUS_TEXT.getOrDefault(t.getStatus(), t.getStatus()));
        p.put("merchantReply", t.getMerchantReply());
        return p;
    }

    private Long merchantUserId(Long merchantId) {
        Merchant m = merchantMapper.selectById(merchantId);
        return m == null ? null : m.getUserId();
    }

    /* ---------- VO ---------- */

    private Map<String, Object> sessionVo(ImSession s, boolean userSide) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", s.getId());
        v.put("orderId", s.getOrderId());
        v.put("userId", s.getUserId());
        v.put("merchantId", s.getMerchantId());
        v.put("status", s.getStatus());
        v.put("lastMsg", s.getLastMsg());
        v.put("lastMsgAt", s.getLastMsgAt() == null ? null : FMT.format(s.getLastMsgAt()));
        v.put("userUnread", s.getUserUnread());
        v.put("merchantUnread", s.getMerchantUnread());
        v.put("unread", userSide ? s.getUserUnread() : s.getMerchantUnread());
        Orders o = ordersMapper.selectById(s.getOrderId());
        if (o != null) {
            v.put("orderNo", o.getOrderNo());
            v.put("payAmount", o.getPayAmount());
            v.put("orderStatus", o.getStatus());
        }
        Merchant merchant = merchantMapper.selectById(s.getMerchantId());
        if (merchant != null) {
            v.put("merchantName", merchant.getShopName());
            v.put("merchantLogo", merchant.getLogo());
        }
        return v;
    }

    private Map<String, Object> messageVo(ImMessage m) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", m.getId());
        v.put("sessionId", m.getSessionId());
        v.put("senderRole", m.getSenderRole());
        v.put("msgType", m.getMsgType());
        v.put("content", m.getContent());
        v.put("createdAt", m.getCreatedAt() == null ? null : FMT.format(m.getCreatedAt()));
        if (m.getPayload() != null && !m.getPayload().isBlank()) {
            try {
                v.put("payload", om.readTree(m.getPayload()));
            } catch (Exception e) {
                v.put("payload", null);
            }
        } else {
            v.put("payload", null);
        }
        return v;
    }

    private Map<String, Object> ticketVo(ImTicket t) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", t.getId());
        v.put("sessionId", t.getSessionId());
        v.put("orderId", t.getOrderId());
        v.put("userId", t.getUserId());
        v.put("merchantId", t.getMerchantId());
        v.put("type", t.getType());
        v.put("typeText", TICKET_TYPE_TEXT.getOrDefault(t.getType(), t.getType()));
        v.put("amount", t.getAmount());
        v.put("reason", t.getReason());
        v.put("images", fromJsonList(t.getImages()));
        v.put("status", t.getStatus());
        v.put("statusText", TICKET_STATUS_TEXT.getOrDefault(t.getStatus(), t.getStatus()));
        v.put("merchantReply", t.getMerchantReply());
        v.put("createdAt", t.getCreatedAt() == null ? null : FMT.format(t.getCreatedAt()));
        v.put("updatedAt", t.getUpdatedAt() == null ? null : FMT.format(t.getUpdatedAt()));
        Orders o = ordersMapper.selectById(t.getOrderId());
        if (o != null) v.put("orderNo", o.getOrderNo());
        return v;
    }

    private String toJson(Object o) {
        try {
            return om.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = om.readTree(json);
            List<String> out = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode n : node) out.add(n.asText());
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
