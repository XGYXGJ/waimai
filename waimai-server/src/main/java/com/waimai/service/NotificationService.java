package com.waimai.service;

import com.waimai.entity.Notification;
import com.waimai.mapper.NotificationMapper;
import com.waimai.websocket.WsPusher;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class NotificationService {

    private final NotificationMapper mapper;
    private final WsPusher wsPusher;

    public NotificationService(NotificationMapper mapper, WsPusher wsPusher) {
        this.mapper = mapper;
        this.wsPusher = wsPusher;
    }

    /** 落库 + WS 实时推送（双通道，前端未连 WS 时轮询兜底） */
    public void notify(Long userId, String type, String title, String content) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(content);
        mapper.insert(n);
        wsPusher.pushNotification(userId, title, content);
    }

    public Map<String, Object> page(Long userId, int page, int size) {
        var p = mapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size),
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Notification>()
                        .eq("user_id", userId).orderByDesc("created_at"));
        return Map.of("records", p.getRecords(), "total", p.getTotal(), "pages", p.getPages());
    }

    public void readAll(Long userId) {
        Notification n = new Notification();
        n.setIsRead(1);
        mapper.update(n, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<Notification>()
                .eq("user_id", userId).eq("is_read", 0));
    }
}
