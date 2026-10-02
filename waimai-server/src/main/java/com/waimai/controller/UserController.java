package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /* ---------- 地址 ---------- */

    @GetMapping("/addresses")
    public R<Map<String, Object>> addresses() {
        return R.ok(userService.addressList(UserContext.userId()));
    }

    @PostMapping("/address")
    public R<Void> saveAddress(@RequestBody WebDTO.AddressReq req) {
        userService.addressSave(UserContext.userId(), req);
        return R.ok();
    }

    @PostMapping("/address/{id}/default")
    public R<Void> setDefault(@PathVariable Long id) {
        userService.setDefault(UserContext.userId(), id);
        return R.ok();
    }

    @DeleteMapping("/address/{id}")
    public R<Void> deleteAddress(@PathVariable Long id) {
        userService.addressDelete(UserContext.userId(), id);
        return R.ok();
    }

    /* ---------- 收藏 ---------- */

    @GetMapping("/favorites")
    public R<Map<String, Object>> favorites() {
        return R.ok(userService.favoriteList(UserContext.userId()));
    }

    @PostMapping("/favorite/{merchantId}")
    public R<Map<String, Object>> toggleFavorite(@PathVariable Long merchantId) {
        return R.ok(userService.favoriteToggle(UserContext.userId(), merchantId));
    }

    /* ---------- 搜索 ---------- */

    @GetMapping("/search-history")
    public R<Map<String, Object>> searchHistory() {
        return R.ok(userService.searchHistory(UserContext.userId()));
    }

    @PostMapping("/search")
    public R<Void> saveSearch(@RequestParam String keyword) {
        userService.saveSearch(UserContext.userId(), keyword);
        return R.ok();
    }

    /* ---------- 行为埋点 ---------- */

    @PostMapping("/behavior")
    public R<Void> behavior(@RequestParam(required = false) Long merchantId,
                            @RequestParam(required = false) Long dishId,
                            @RequestParam String action) {
        userService.logBehavior(UserContext.userId(), merchantId, dishId, action);
        return R.ok();
    }
}
