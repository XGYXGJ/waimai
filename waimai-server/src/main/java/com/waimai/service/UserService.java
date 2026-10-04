package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.waimai.common.exception.BizException;
import com.waimai.common.result.ResultCode;
import com.waimai.dto.WebDTO;
import com.waimai.entity.Address;
import com.waimai.entity.Favorite;
import com.waimai.entity.SearchHistory;
import com.waimai.entity.UserBehavior;
import com.waimai.mapper.AddressMapper;
import com.waimai.mapper.FavoriteMapper;
import com.waimai.mapper.SearchHistoryMapper;
import com.waimai.mapper.UserBehaviorMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserService {

    private final AddressMapper addressMapper;
    private final FavoriteMapper favoriteMapper;
    private final SearchHistoryMapper searchHistoryMapper;
    private final UserBehaviorMapper behaviorMapper;

    /* ---------- 地址 ---------- */

    public Map<String, Object> addressList(Long userId) {
        return Map.of("records", addressMapper.selectList(
                new QueryWrapper<Address>().eq("user_id", userId).orderByDesc("is_default").orderByDesc("created_at")));
    }

    public Long addressSave(Long userId, WebDTO.AddressReq req) {
        // 之前这里不做任何校验，字段缺失/为空会直接捅到数据库抛
        // DataIntegrityViolationException，前端只看到「系统繁忙」。
        if (isBlank(req.getContact())) throw new BizException("请填写联系人");
        String phone = req.getPhone() == null ? "" : req.getPhone().trim();
        if (!phone.matches("^1\\d{10}$")) throw new BizException("请填写正确的 11 位手机号");
        if (isBlank(req.getDetail())) throw new BizException("请填写详细地址（方便骑手送达）");

        Address a = req.getId() == null ? new Address() : requireOwned(req.getId(), userId);
        a.setUserId(userId);
        a.setContact(req.getContact().trim());
        a.setPhone(phone);
        a.setGender(req.getGender() == null ? 1 : req.getGender());
        // 省市区表单不采集，统一存空串而不是 null：
        // MyBatis-Plus 会跳过 null 字段，列在 INSERT 里直接消失，
        // 一旦该列 NOT NULL 无默认值就会报 "doesn't have a default value"。
        a.setProvince(blankToEmpty(req.getProvince()));
        a.setCity(blankToEmpty(req.getCity()));
        a.setDistrict(blankToEmpty(req.getDistrict()));
        a.setDetail(req.getDetail().trim());
        // 经纬度可空：未在地图上选点时允许只填文字地址
        a.setLng(req.getLng() == null ? null : java.math.BigDecimal.valueOf(req.getLng()));
        a.setLat(req.getLat() == null ? null : java.math.BigDecimal.valueOf(req.getLat()));
        a.setTag(req.getTag());
        if (a.getId() == null) {
            // 首个地址自动设为默认
            Long count = addressMapper.selectCount(new QueryWrapper<Address>().eq("user_id", userId));
            a.setIsDefault(count == null || count == 0 ? 1 : 0);
            addressMapper.insert(a);
        } else {
            addressMapper.updateById(a);
        }
        // 唯一默认地址
        if (req.getIsDefault() != null && req.getIsDefault() == 1) {
            setDefault(userId, a.getId());
        }
        return a.getId();
    }

    public void setDefault(Long userId, Long addressId) {
        requireOwned(addressId, userId);
        Address reset = new Address();
        reset.setIsDefault(0);
        addressMapper.update(reset, new QueryWrapper<Address>().eq("user_id", userId));
        Address one = new Address();
        one.setId(addressId);
        one.setIsDefault(1);
        addressMapper.updateById(one);
    }

    public void addressDelete(Long userId, Long addressId) {
        requireOwned(addressId, userId);
        addressMapper.deleteById(addressId);
    }

    private Address requireOwned(Long id, Long userId) {
        Address a = addressMapper.selectById(id);
        if (a == null || !a.getUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
        }
        return a;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String blankToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    /* ---------- 收藏 ---------- */

    public Map<String, Object> favoriteList(Long userId) {
        return Map.of("records", favoriteMapper.selectList(
                new QueryWrapper<Favorite>().eq("user_id", userId).orderByDesc("created_at")));
    }

    public Map<String, Object> favoriteToggle(Long userId, Long merchantId) {
        Favorite exist = favoriteMapper.selectOne(new QueryWrapper<Favorite>()
                .eq("user_id", userId).eq("merchant_id", merchantId));
        if (exist != null) {
            favoriteMapper.deleteById(exist.getId());
            return Map.of("favorited", false);
        }
        Favorite f = new Favorite();
        f.setUserId(userId);
        f.setMerchantId(merchantId);
        favoriteMapper.insert(f);
        logBehavior(userId, merchantId, null, "FAV");
        return Map.of("favorited", true);
    }

    /* ---------- 搜索历史 ---------- */

    public void saveSearch(Long userId, String keyword) {
        if (keyword == null || keyword.isBlank()) return;
        SearchHistory h = new SearchHistory();
        h.setUserId(userId);
        h.setKeyword(keyword.trim());
        searchHistoryMapper.insert(h);
    }

    public Map<String, Object> searchHistory(Long userId) {
        var list = searchHistoryMapper.selectList(new QueryWrapper<SearchHistory>()
                .eq("user_id", userId).orderByDesc("created_at").last("limit 10"));
        return Map.of("records", list);
    }

    /* ---------- 行为埋点（推荐数据源） ---------- */

    public void logBehavior(Long userId, Long merchantId, Long dishId, String action) {
        UserBehavior b = new UserBehavior();
        b.setUserId(userId);
        b.setMerchantId(merchantId);
        b.setDishId(dishId);
        b.setAction(action);
        behaviorMapper.insert(b);
    }
}
