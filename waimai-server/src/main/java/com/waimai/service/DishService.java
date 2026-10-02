package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.dto.WebDTO;
import com.waimai.entity.Dish;
import com.waimai.entity.DishCategory;
import com.waimai.mapper.DishCategoryMapper;
import com.waimai.mapper.DishMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DishService {

    private final DishMapper dishMapper;
    private final DishCategoryMapper dishCategoryMapper;
    private final MerchantService merchantService;
    private final StringRedisTemplate redis;

    /* ---------- 菜品分类 ---------- */

    public List<DishCategory> categories(Long merchantUserId) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        return dishCategoryMapper.selectList(new QueryWrapper<DishCategory>()
                .eq("merchant_id", merchantId).orderByAsc("sort"));
    }

    public void saveCategory(Long merchantUserId, Long id, String name, Integer sort) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        DishCategory c = id == null ? new DishCategory() : dishCategoryMapper.selectById(id);
        if (c == null || (id != null && !c.getMerchantId().equals(merchantId))) {
            throw new BizException("分类不存在");
        }
        c.setMerchantId(merchantId);
        c.setName(name);
        c.setSort(sort == null ? 0 : sort);
        if (c.getId() == null) dishCategoryMapper.insert(c);
        else dishCategoryMapper.updateById(c);
    }

    public void deleteCategory(Long merchantUserId, Long id) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        DishCategory c = dishCategoryMapper.selectById(id);
        if (c == null || !c.getMerchantId().equals(merchantId)) throw new BizException("分类不存在");
        Long cnt = dishMapper.selectCount(new QueryWrapper<Dish>().eq("category_id", id));
        if (cnt > 0) throw new BizException("该分类下仍有菜品，请先移除");
        dishCategoryMapper.deleteById(id);
    }

    /* ---------- 菜品 ---------- */

    public Map<String, Object> dishes(Long merchantUserId, Long categoryId) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        QueryWrapper<Dish> qw = new QueryWrapper<Dish>().eq("merchant_id", merchantId)
                .eq(categoryId != null, "category_id", categoryId)
                .orderByAsc("category_id").orderByDesc("created_at");
        return Map.of("records", dishMapper.selectList(qw));
    }

    public void save(Long merchantUserId, WebDTO.DishReq req) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        Dish d = req.getId() == null ? new Dish() : dishMapper.selectById(req.getId());
        if (d == null || (req.getId() != null && !d.getMerchantId().equals(merchantId))) {
            throw new BizException("菜品不存在");
        }
        d.setMerchantId(merchantId);
        if (req.getCategoryId() != null) d.setCategoryId(req.getCategoryId());
        if (req.getName() != null) d.setName(req.getName());
        if (req.getDescription() != null) d.setDescription(req.getDescription());
        if (req.getImage() != null) d.setImage(req.getImage());
        if (req.getPrice() != null) d.setPrice(java.math.BigDecimal.valueOf(req.getPrice()));
        if (req.getOriginalPrice() != null) d.setOriginalPrice(java.math.BigDecimal.valueOf(req.getOriginalPrice()));
        if (req.getUnit() != null) d.setUnit(req.getUnit());
        if (req.getTags() != null) d.setTags(req.getTags());
        if (req.getIsRecommend() != null) d.setIsRecommend(req.getIsRecommend());
        if (req.getStatus() != null) d.setStatus(req.getStatus());
        if (d.getId() == null) {
            d.setStock(req.getStock() == null ? 999 : req.getStock());
            d.setStatus(req.getStatus() == null ? 1 : req.getStatus());
            dishMapper.insert(d);
        } else {
            // 库存变更同步 Redis
            if (req.getStock() != null && !req.getStock().equals(d.getStock())) {
                d.setStock(req.getStock());
                redis.opsForValue().set("dish:stock:" + d.getId(), String.valueOf(req.getStock()));
            }
            dishMapper.updateById(d);
        }
        // 新菜品初始化库存缓存
        redis.opsForValue().set("dish:stock:" + d.getId(), String.valueOf(d.getStock()));
    }

    /** 上架/下架 */
    public void changeStatus(Long merchantUserId, Long dishId, Integer status) {
        Long merchantId = merchantService.merchantOfUser(merchantUserId).getId();
        Dish d = dishMapper.selectById(dishId);
        if (d == null || !d.getMerchantId().equals(merchantId)) throw new BizException("菜品不存在");
        d.setStatus(status);
        dishMapper.updateById(d);
    }

    public Map<String, Object> searchDishes(String keyword) {
        List<Dish> list = dishMapper.selectList(new QueryWrapper<Dish>()
                .eq("status", 1).like("name", keyword).orderByDesc("monthly_sales").last("limit 30"));
        List<Map<String, Object>> records = list.stream().map(d -> {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("dishId", d.getId());
            m.put("dishName", d.getName());
            m.put("image", d.getImage());
            m.put("price", d.getPrice());
            m.put("merchantId", d.getMerchantId());
            m.put("monthlySales", d.getMonthlySales());
            return m;
        }).toList();
        return Map.of("records", records);
    }
}
