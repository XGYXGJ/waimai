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
import com.waimai.entity.Rider;
import com.waimai.mapper.ImMessageMapper;
import com.waimai.mapper.ImSessionMapper;
import com.waimai.mapper.ImTicketMapper;
import com.waimai.mapper.MerchantMapper;
import com.waimai.mapper.OrderItemMapper;
import com.waimai.mapper.OrdersMapper;
import com.waimai.mapper.RiderMapper;
import com.waimai.websocket.WsPusher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 订单会话（用户 + 商家 + 骑手三方）与售后工单。
 *
 * 会话以订单为单位（im_session.order_id 唯一），会话内消息分四类：
 * TEXT 文本 / IMAGE 图片 / ORDER 订单卡片 / TICKET 工单卡片。
 *
 * <p>三方能力边界：
 * <ul>
 *   <li>用户：发文本 / 图片，可在会话内带出订单卡片，发起售后工单</li>
 *   <li>商家：发文本 / 图片，可发工单卡片、答复工单（处理动作）</li>
 *   <li>骑手：发文本 / 图片（只读工单，不参与资金处置）</li>
 * </ul>
 *
 * <p>会话有效期：从订单开始（商家接单）到<b>送达后 30 分钟</b>。
 * 送达前由订单状态决定是否可进入，送达后按 close_at 截止 —— 留 30 分钟是为了处理
 * 「餐洒了/少送了/骑手找不到门」这类必须立刻沟通的售后。
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
    private final RiderMapper riderMapper;
    private final NotificationService notificationService;
    private final WsPusher wsPusher;

    private final ObjectMapper om = new ObjectMapper();

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Set<String> MSG_TYPES = Set.of("TEXT", "IMAGE", "ORDER", "TICKET");
    private static final Set<String> TICKET_TYPES = Set.of("REFUND", "COMPENSATE", "REISSUE", "OTHER");
    private static final Set<String> TICKET_ACTIONS = Set.of("PROCESSING", "APPROVED", "REJECTED", "CLOSED");

    /** 三方角色 */
    public static final String SIDE_USER = "USER";
    public static final String SIDE_MERCHANT = "MERCHANT";
    public static final String SIDE_RIDER = "RIDER";

    /** 送达后会话保留时长（分钟）：处理「餐洒了 / 少送了 / 找不到门」这类必须立刻沟通的售后 */
    public static final int CHAT_KEEP_MINUTES_AFTER_DELIVERED = 30;

    /** 各方可发送的消息类型。骑手不参与资金处置，因此不能发 ORDER / TICKET 卡片 */
    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            SIDE_USER, Set.of("TEXT", "IMAGE", "ORDER"),
            SIDE_MERCHANT, Set.of("TEXT", "IMAGE", "TICKET"),
            SIDE_RIDER, Set.of("TEXT", "IMAGE"));

    private static final Map<String, String> TICKET_TYPE_TEXT = Map.of(
            "REFUND", "退款", "COMPENSATE", "赔偿", "REISSUE", "补发", "OTHER", "其他");
    private static final Map<String, String> TICKET_STATUS_TEXT = Map.of(
            "PENDING", "待处理", "PROCESSING", "处理中", "APPROVED", "已同意", "REJECTED", "已驳回", "CLOSED", "已关闭");

    /* ==================== 用户端 ==================== */

    /**
     * 按订单开启会话（幂等：已有则直接返回）。
     *
     * <p>订单未接单时也允许建会话（用户可能想先问一句），但此时 chatOpen=false，
     * 前端会把输入框置灰并说明原因 —— 校验放在 {@link #ensureChatOpen}，前端只是提示层。
     */
    public Map<String, Object> openSession(Long userId, Long orderId) {
        if (orderId == null) throw new BizException("订单 ID 不能为空");
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        if (!Objects.equals(order.getUserId(), userId)) throw new BizException("无权访问该订单");
        ImSession s = getOrCreate(order);
        syncRider(s);
        return sessionVo(s, SIDE_USER);
    }

    public List<Map<String, Object>> mySessions(Long userId) {
        return sessionMapper.selectList(new QueryWrapper<ImSession>()
                        .eq("user_id", userId).orderByDesc("updated_at"))
                .stream().map(s -> {
                    syncRider(s);
                    return sessionVo(s, SIDE_USER);
                }).collect(Collectors.toList());
    }

    /**
     * 拉取消息。sinceId 用于增量轮询；进入会话时顺带把本侧未读清零。
     *
     * @param side       调用方角色 USER / MERCHANT / RIDER
     * @param operatorId 用户端传 userId，商户端传 merchantId，骑手端传 riderId，用于越权校验
     */
    public Map<String, Object> messages(Long sessionId, Long sinceId, String side, Long operatorId) {
        ImSession s = requireSession(sessionId);
        checkOwner(s, side, operatorId);
        syncRider(s);
        QueryWrapper<ImMessage> q = new QueryWrapper<ImMessage>().eq("session_id", s.getId());
        if (sinceId != null && sinceId > 0) q.gt("id", sinceId);
        q.orderByAsc("id").last("limit 500");
        List<ImMessage> list = messageMapper.selectList(q);

        clearUnread(s, side);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("session", sessionVo(s, side));
        out.put("records", list.stream().map(this::messageVo).collect(Collectors.toList()));
        out.put("lastId", list.isEmpty() ? (sinceId == null ? 0L : sinceId)
                : list.get(list.size() - 1).getId());
        return out;
    }

    /**
     * 发消息：side = USER / MERCHANT / RIDER。
     *
     * <p>会话有效期之外直接拒绝 —— 「送达后 30 分钟」的约定必须由服务端兜住，
     * 只在前端隐藏入口的话，改一下前端就能继续发言。
     */
    public Map<String, Object> send(Long sessionId, String side, Long operatorId, WebDTO.ImSendReq req) {
        ImSession s = requireSession(sessionId);
        checkOwner(s, side, operatorId);
        ensureChatOpen(s);
        syncRider(s);
        if (req == null) throw new BizException("请求参数不能为空");

        String type = req.getMsgType() == null || req.getMsgType().isBlank()
                ? "TEXT" : req.getMsgType().trim().toUpperCase();
        if (!MSG_TYPES.contains(type)) throw new BizException("不支持的消息类型：" + type);
        Set<String> allowed = ALLOWED_TYPES.getOrDefault(side, Set.of("TEXT"));
        if (!allowed.contains(type)) {
            throw new BizException(sideLabel(side) + "不能发送该类型的消息");
        }

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
        } else if ("TICKET".equals(type)) {
            // 商家发工单卡片：复用已有工单，缺省取该会话最新一张
            payload = latestTicketPayload(s);
            if (content.isEmpty()) content = "[工单]";
        } else {
            if (content.isEmpty()) throw new BizException("消息内容不能为空");
            if (content.length() > 1000) content = content.substring(0, 1000);
        }

        if ("CLOSED".equals(s.getStatus())) {
            s.setStatus("OPEN");
            sessionMapper.updateById(s);
        }

        ImMessage m = insertMessage(s, side, type, content, payload);
        touchSession(s, content);
        incrUnread(s, side);

        // WS 秒达三方；轮询作为兜底保留
        try {
            wsPusher.pushImMessage(s.getId(), messageVo(m), receiverUserIds(s));
        } catch (Exception e) {
            log.warn("im ws push failed: {}", e.getMessage());
        }

        // 对方不在会话页时给一条站内通知
        try {
            notifyOthers(s, side, content);
        } catch (Exception e) {
            log.warn("im notify failed: {}", e.getMessage());
        }
        return messageVo(m);
    }

    /** 商家发工单卡片时带上最近一张工单的完整信息 */
    private Map<String, Object> latestTicketPayload(ImSession s) {
        ImTicket t = ticketMapper.selectOne(new QueryWrapper<ImTicket>()
                .eq("session_id", s.getId()).orderByDesc("id").last("limit 1"));
        if (t == null) throw new BizException("当前会话还没有售后工单");
        return ticketPayload(t, fromJsonList(t.getImages()));
    }

    /** 会话三方对应的登录 userId（骑手可能还没接单，此时为 null） */
    private List<Long> receiverUserIds(ImSession s) {
        List<Long> ids = new ArrayList<>();
        if (s.getUserId() != null) ids.add(s.getUserId());
        Long mu = merchantUserId(s.getMerchantId());
        if (mu != null) ids.add(mu);
        if (s.getRiderId() != null) {
            Rider r = riderMapper.selectById(s.getRiderId());
            if (r != null && r.getUserId() != null) ids.add(r.getUserId());
        }
        return ids;
    }

    /** 给「除发送方之外」的另外两方发站内通知 */
    private void notifyOthers(ImSession s, String side, String content) {
        String title = switch (side) {
            case SIDE_USER -> "顾客发来新消息";
            case SIDE_MERCHANT -> "商家回复了你";
            case SIDE_RIDER -> "骑手发来新消息";
            default -> "订单有新消息";
        };
        if (!SIDE_USER.equals(side) && s.getUserId() != null) {
            notificationService.notify(s.getUserId(), "IM", title, content);
        }
        if (!SIDE_MERCHANT.equals(side)) {
            Long mu = merchantUserId(s.getMerchantId());
            if (mu != null) notificationService.notify(mu, "IM", title, content);
        }
        if (!SIDE_RIDER.equals(side) && s.getRiderId() != null) {
            Rider r = riderMapper.selectById(s.getRiderId());
            if (r != null && r.getUserId() != null) {
                notificationService.notify(r.getUserId(), "IM", title, content);
            }
        }
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
            wsPusher.pushImMessage(s.getId(), messageVo(m), receiverUserIds(s));
        } catch (Exception e) {
            log.warn("ticket ws push failed: {}", e.getMessage());
        }

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
                .stream().map(s -> {
                    syncRider(s);
                    return sessionVo(s, SIDE_MERCHANT);
                }).collect(Collectors.toList());
    }

    /* ==================== 骑手端 ==================== */

    /** 骑手可见的会话：只与自己接过的订单相关，且仍在沟通有效期内 */
    public List<Map<String, Object>> riderSessions(Long userId) {
        Long riderId = riderIdOf(userId);
        // 先取该骑手接过的全部订单，再反查会话 —— 会话可能在接单前就已由用户创建，
        // 那时 rider_id 还是空，用 im_session.rider_id 直接查会漏掉。
        List<Long> orderIds = ordersMapper.selectList(new QueryWrapper<Orders>()
                        .eq("rider_id", riderId).select("id"))
                .stream().map(Orders::getId).toList();
        if (orderIds.isEmpty()) return List.of();
        return sessionMapper.selectList(new QueryWrapper<ImSession>()
                        .in("order_id", orderIds).orderByDesc("updated_at"))
                .stream().map(s -> {
                    syncRider(s);
                    return sessionVo(s, SIDE_RIDER);
                }).filter(v -> Boolean.TRUE.equals(v.get("chatOpen")))
                .collect(Collectors.toList());
    }

    /** 骑手 ID（按登录用户解析） */
    public Long riderIdOf(Long userId) {
        Rider r = riderMapper.selectOne(new QueryWrapper<Rider>().eq("user_id", userId));
        if (r == null) throw new BizException("当前账号不是骑手");
        return r.getId();
    }

    /** 骑手按订单开启会话（接单后才有意义，越权校验走订单的 rider_id） */
    public Map<String, Object> riderOpenSession(Long userId, Long orderId) {
        if (orderId == null) throw new BizException("订单 ID 不能为空");
        Orders order = ordersMapper.selectById(orderId);
        if (order == null) throw new BizException("订单不存在");
        Long riderId = riderIdOf(userId);
        if (!Objects.equals(order.getRiderId(), riderId)) throw new BizException("该订单不由你配送");
        ImSession s = getOrCreate(order);
        syncRider(s);
        return sessionVo(s, SIDE_RIDER);
    }

    /** 骑手可读的工单列表（只读：工单涉及资金，由商家处置） */
    public List<Map<String, Object>> riderTickets(Long userId) {
        Long riderId = riderIdOf(userId);
        List<Long> orderIds = ordersMapper.selectList(new QueryWrapper<Orders>()
                        .eq("rider_id", riderId).select("id"))
                .stream().map(Orders::getId).toList();
        if (orderIds.isEmpty()) return List.of();
        return ticketMapper.selectList(new QueryWrapper<ImTicket>()
                        .in("order_id", orderIds).orderByDesc("created_at"))
                .stream().map(this::ticketVo).collect(Collectors.toList());
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
                wsPusher.pushImMessage(s.getId(), messageVo(m), receiverUserIds(s));
            } catch (Exception e) {
                log.warn("ticket result ws push failed: {}", e.getMessage());
            }
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
        s.setRiderId(order.getRiderId());   // 骑手接单后建的会话直接带上；接单前建的后续由 syncRider 回填
        s.setStatus("OPEN");
        s.setUserUnread(0);
        s.setMerchantUnread(0);
        s.setRiderUnread(0);
        sessionMapper.insert(s);

        // 开会话时自动带出一张订单卡片，双方都不用重复描述订单
        insertMessage(s, "SYSTEM", "ORDER", "[订单]", orderSnapshot(order.getId()));
        touchSession(s, "[订单]");
        return s;
    }

    /** 越权校验：三方各自比对会话里对应的那一列 */
    private void checkOwner(ImSession s, String side, Long operatorId) {
        if (operatorId == null) throw new BizException("未登录");
        Long expect = switch (side) {
            case SIDE_USER -> s.getUserId();
            case SIDE_MERCHANT -> s.getMerchantId();
            case SIDE_RIDER -> s.getRiderId();
            default -> throw new BizException("未知的会话角色");
        };
        if (expect == null) {
            if (SIDE_RIDER.equals(side)) throw new BizException("该订单还没有骑手接单");
            throw new BizException("无权访问该会话");
        }
        if (!Objects.equals(expect, operatorId)) throw new BizException("无权访问该会话");
    }

    /**
     * 会话有效期校验：订单开始（商家接单）到送达后 {@value #CHAT_KEEP_MINUTES_AFTER_DELIVERED} 分钟。
     *
     * <p>送达时间以订单快照为准，不用会话表里的 close_at，避免两处时间不一致。
     *
     * <p>判定顺序很重要：先「订单是否已结束」→ 再「是否已开单/配送中」→ 最后 30 分钟窗口。
     * 之前用 {@code acceptTime == null && 状态不在白名单} 判「未开单」，产生两种误判：
     * <ul>
     *   <li>DELIVERED 但 accept_time 为空的历史 / 导入数据被当成「商家还没接单」而拒掉；</li>
     *   <li>PAID→REFUNDED、PENDING_PAYMENT→CANCELLED 也报「商家接单后才可以沟通」，
     *       掩盖了真实原因（订单已结束）。</li>
     * </ul>
     */
    private void ensureChatOpen(ImSession s) {
        Orders o = ordersMapper.selectById(s.getOrderId());
        if (o == null) throw new BizException("订单不存在");

        // 1) 订单已结束：取消 / 退款，没有继续沟通的意义
        if ("CANCELLED".equals(o.getStatus()) || "REFUNDED".equals(o.getStatus())) {
            throw new BizException("订单已结束，无法继续沟通");
        }

        // 2) 已送达：只留 30 分钟售后窗口。这里只看 delivered_time，不看 accept_time ——
        //    后者对历史数据可能为空，不该拿它当「是否送达」的判据。
        if ("DELIVERED".equals(o.getStatus()) || o.getDeliveredTime() != null) {
            LocalDateTime delivered = o.getDeliveredTime();
            if (delivered == null) return;   // 状态已是送达但缺时间戳：按刚送达处理，不额外拦
            LocalDateTime deadline = delivered.plusMinutes(CHAT_KEEP_MINUTES_AFTER_DELIVERED);
            if (LocalDateTime.now().isAfter(deadline)) {
                throw new BizException("订单已送达超过 "
                        + CHAT_KEEP_MINUTES_AFTER_DELIVERED + " 分钟，如需帮助请联系客服");
            }
            return;
        }

        // 3) 配送中：正常开放
        if ("ACCEPTED".equals(o.getStatus())
                || "WAITING_PICKUP".equals(o.getStatus())
                || "DELIVERING".equals(o.getStatus())) {
            return;
        }

        // 4) 其余（PENDING_PAYMENT / PAID）尚未开单
        throw new BizException("商家接单后才可以发起沟通");
    }

    /** 会话是否处于可沟通状态（前端用来决定入口是否可点） */
    public boolean chatOpen(Long sessionId) {
        try {
            ensureChatOpen(requireSession(sessionId));
            return true;
        } catch (BizException e) {
            return false;
        }
    }

    /**
     * 回填会话里的骑手：接单发生在会话创建之后，所以每次读会话都要看一眼订单上的
     * rider_id 有没有新值。旧会话在迁移脚本里回填过一次，这里覆盖「新接单」的情况。
     */
    private void syncRider(ImSession s) {
        Orders o = ordersMapper.selectById(s.getOrderId());
        if (o == null || o.getRiderId() == null) return;
        if (Objects.equals(s.getRiderId(), o.getRiderId())) return;
        s.setRiderId(o.getRiderId());
        if (s.getRiderUnread() == null) s.setRiderUnread(0);
        sessionMapper.updateById(s);
    }

    private static String sideLabel(String side) {
        return switch (side) {
            case SIDE_USER -> "用户";
            case SIDE_MERCHANT -> "商家";
            case SIDE_RIDER -> "骑手";
            default -> "该角色";
        };
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

    /** 发件方的对侧未读 +1（其余两方各 +1） */
    private void incrUnread(ImSession s, String senderRole) {
        if (!SIDE_MERCHANT.equals(senderRole)) {
            s.setMerchantUnread((s.getMerchantUnread() == null ? 0 : s.getMerchantUnread()) + 1);
        }
        if (!SIDE_USER.equals(senderRole)) {
            s.setUserUnread((s.getUserUnread() == null ? 0 : s.getUserUnread()) + 1);
        }
        if (!SIDE_RIDER.equals(senderRole) && s.getRiderId() != null) {
            s.setRiderUnread((s.getRiderUnread() == null ? 0 : s.getRiderUnread()) + 1);
        }
        sessionMapper.updateById(s);
    }

    private void clearUnread(ImSession s, String side) {
        boolean dirty = false;
        Integer v;
        switch (side) {
            case SIDE_USER -> v = s.getUserUnread();
            case SIDE_MERCHANT -> v = s.getMerchantUnread();
            case SIDE_RIDER -> v = s.getRiderUnread();
            default -> v = null;
        }
        if (v != null && v > 0) {
            switch (side) {
                case SIDE_USER -> s.setUserUnread(0);
                case SIDE_MERCHANT -> s.setMerchantUnread(0);
                case SIDE_RIDER -> s.setRiderUnread(0);
                default -> { }
            }
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

    private Map<String, Object> sessionVo(ImSession s, String side) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", s.getId());
        v.put("orderId", s.getOrderId());
        v.put("userId", s.getUserId());
        v.put("merchantId", s.getMerchantId());
        v.put("riderId", s.getRiderId());
        v.put("status", s.getStatus());
        v.put("lastMsg", s.getLastMsg());
        v.put("lastMsgAt", s.getLastMsgAt() == null ? null : FMT.format(s.getLastMsgAt()));
        v.put("userUnread", s.getUserUnread());
        v.put("merchantUnread", s.getMerchantUnread());
        v.put("riderUnread", s.getRiderUnread());
        v.put("unread", switch (side == null ? SIDE_USER : side) {
            case SIDE_MERCHANT -> s.getMerchantUnread();
            case SIDE_RIDER -> s.getRiderUnread();
            default -> s.getUserUnread();
        });
        // 各方可发的消息类型：前端据此决定「发订单卡片 / 发工单」的按钮显不显示
        v.put("allowedTypes", new ArrayList<>(ALLOWED_TYPES.getOrDefault(
                side == null ? SIDE_USER : side, Set.of("TEXT"))));
        Orders o = ordersMapper.selectById(s.getOrderId());
        if (o != null) {
            v.put("orderNo", o.getOrderNo());
            v.put("payAmount", o.getPayAmount());
            v.put("orderStatus", o.getStatus());
            // 送达后 30 分钟：前端据此提示「沟通即将关闭」
            if (o.getDeliveredTime() != null) {
                v.put("chatDeadline", FMT.format(o.getDeliveredTime()
                        .plusMinutes(CHAT_KEEP_MINUTES_AFTER_DELIVERED)));
            }
        }
        v.put("chatOpen", chatOpen(s.getId()));
        Merchant merchant = merchantMapper.selectById(s.getMerchantId());
        if (merchant != null) {
            v.put("merchantName", merchant.getShopName());
            v.put("merchantLogo", merchant.getLogo());
        }
        if (s.getRiderId() != null) {
            Rider r = riderMapper.selectById(s.getRiderId());
            if (r != null) {
                v.put("riderName", r.getRealName());
                v.put("riderPhone", r.getPhone());
            }
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
