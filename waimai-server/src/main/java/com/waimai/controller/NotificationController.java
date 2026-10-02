package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        return R.ok(notificationService.page(UserContext.userId(), page, size));
    }

    @PostMapping("/read-all")
    public R<Void> readAll() {
        notificationService.readAll(UserContext.userId());
        return R.ok();
    }
}
