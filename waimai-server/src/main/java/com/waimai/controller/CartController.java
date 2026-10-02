package com.waimai.controller;

import com.waimai.common.context.UserContext;
import com.waimai.common.result.R;
import com.waimai.dto.WebDTO;
import com.waimai.service.CartService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public R<Map<String, Object>> list() {
        return R.ok(cartService.list(UserContext.userId()));
    }

    @PostMapping("/add")
    public R<Void> add(@RequestParam Long dishId, @RequestParam(defaultValue = "1") Integer quantity) {
        cartService.add(UserContext.userId(), dishId, quantity);
        return R.ok();
    }

    @PostMapping("/update")
    public R<Void> update(@RequestParam Long dishId, @RequestParam Integer quantity) {
        cartService.updateQty(UserContext.userId(), dishId, quantity);
        return R.ok();
    }

    @DeleteMapping("/clear")
    public R<Void> clear() {
        cartService.clear(UserContext.userId());
        return R.ok();
    }
}
