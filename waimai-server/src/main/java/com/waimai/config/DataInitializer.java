package com.waimai.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.entity.*;
import com.waimai.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首次启动初始化：管理员账号 + 演示账号（用户/商户/骑手）+ 演示商家/分类/菜品 + 默认系统配置。
 * 仅当 user 表为空时执行，避免重复插入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserMapper userMapper;
    private final MerchantMapper merchantMapper;
    private final RiderMapper riderMapper;
    private final MerchantCategoryMapper merchantCategoryMapper;
    private final DishCategoryMapper dishCategoryMapper;
    private final DishMapper dishMapper;
    private final SysConfigMapper sysConfigMapper;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void run(String... args) {
        Long count = userMapper.selectCount(null);
        if (count != null && count > 0) {
            log.info("[DataInitializer] user 表已有 {} 条数据，跳过初始化", count);
            initSysConfig(); // 配置项每次都兜底
            return;
        }
        log.info("[DataInitializer] 首次启动，开始初始化演示数据...");

        // 1. 账号
        User admin = newUser("admin", "admin123", "平台管理员", "ADMIN");
        User user = newUser("13800000001", "123456", "演示用户小明", "USER");
        User merchantUser = newUser("13800000002", "123456", "张记小厨", "MERCHANT");
        User riderUser = newUser("13800000003", "123456", "骑手小李", "RIDER");
        userMapper.insert(admin);
        userMapper.insert(user);
        userMapper.insert(merchantUser);
        userMapper.insert(riderUser);

        // 2. 商家分类
        MerchantCategory mc1 = new MerchantCategory();
        mc1.setName("快餐便当"); mc1.setSort(1);
        MerchantCategory mc2 = new MerchantCategory();
        mc2.setName("奶茶甜点"); mc2.setSort(2);
        MerchantCategory mc3 = new MerchantCategory();
        mc3.setName("小吃夜宵"); mc3.setSort(3);
        merchantCategoryMapper.insert(mc1);
        merchantCategoryMapper.insert(mc2);
        merchantCategoryMapper.insert(mc3);

        // 3. 演示商户（审核通过、营业中）
        Merchant m = new Merchant();
        m.setUserId(merchantUser.getId());
        m.setShopName("张记小厨");
        m.setCategoryId(mc1.getId());
        m.setNotice("本店今日满30减5，欢迎下单~");
        m.setPhone("13800000002");
        m.setAddress("北京市海淀区中关村大街1号");
        m.setLng(new BigDecimal("116.397128"));
        m.setLat(new BigDecimal("39.916527"));
        m.setBusinessHours("09:00-21:00");
        m.setMinOrderAmount(new BigDecimal("20.00"));
        m.setDeliveryFee(new BigDecimal("3.00"));
        m.setRating(new BigDecimal("4.8"));
        m.setMonthlySales(1250);
        m.setOpenStatus(1);
        m.setAuditStatus(1);
        merchantMapper.insert(m);

        // 4. 菜品分类
        DishCategory dc1 = new DishCategory();
        dc1.setMerchantId(m.getId()); dc1.setName("招牌热卖"); dc1.setSort(1);
        DishCategory dc2 = new DishCategory();
        dc2.setMerchantId(m.getId()); dc2.setName("主食"); dc2.setSort(2);
        DishCategory dc3 = new DishCategory();
        dc3.setMerchantId(m.getId()); dc3.setName("饮品"); dc3.setSort(3);
        dishCategoryMapper.insert(dc1);
        dishCategoryMapper.insert(dc2);
        dishCategoryMapper.insert(dc3);

        // 5. 菜品
        insertDish(m.getId(), dc1.getId(), "招牌红烧肉饭", "肥而不腻，入口即化", new BigDecimal("28.00"), new BigDecimal("32.00"), "辣,下饭", 1);
        insertDish(m.getId(), dc1.getId(), "宫保鸡丁饭", "经典川味，微辣", new BigDecimal("22.00"), new BigDecimal("26.00"), "微辣,热销", 1);
        insertDish(m.getId(), dc2.getId(), "扬州炒饭", "粒粒分明，配料丰富", new BigDecimal("18.00"), null, "主食", 0);
        insertDish(m.getId(), dc2.getId(), "番茄鸡蛋面", "酸甜开胃", new BigDecimal("15.00"), null, "面食", 0);
        insertDish(m.getId(), dc3.getId(), "冰镇酸梅汤", "解腻神器", new BigDecimal("6.00"), null, "饮品", 0);
        insertDish(m.getId(), dc3.getId(), "柠檬绿茶", "清爽解渴", new BigDecimal("8.00"), null, "饮品", 0);

        // 6. 演示骑手（审核通过、可接单）
        Rider r = new Rider();
        r.setUserId(riderUser.getId());
        r.setRealName("李小骑");
        r.setPhone("13800000003");
        r.setVehicle("电动车");
        r.setAuditStatus(1);
        r.setWorkStatus(1);
        r.setTodayOrders(0);
        riderMapper.insert(r);

        initSysConfig();
        log.info("[DataInitializer] 演示数据初始化完成。管理员 admin/admin123；用户/商户/骑手 13800000001~03 / 123456");
    }

    private User newUser(String phone, String pwd, String nickname, String role) {
        User u = new User();
        u.setPhone(phone);
        u.setPasswordHash(encoder.encode(pwd));
        u.setNickname(nickname);
        u.setRole(role);
        u.setStatus(1);
        return u;
    }

    private void insertDish(Long merchantId, Long categoryId, String name, String desc,
                            BigDecimal price, BigDecimal originalPrice, String tags, int isRecommend) {
        Dish d = new Dish();
        d.setMerchantId(merchantId);
        d.setCategoryId(categoryId);
        d.setName(name);
        d.setDescription(desc);
        d.setPrice(price);
        d.setOriginalPrice(originalPrice);
        d.setUnit("份");
        d.setStock(999);
        d.setMonthlySales(0);
        d.setRating(new BigDecimal("4.8"));
        d.setTags(tags);
        d.setIsRecommend(isRecommend);
        d.setStatus(1);
        dishMapper.insert(d);
    }

    /** 系统配置兜底（每次启动都确保默认配置存在） */
    private void initSysConfig() {
        putIfAbsent("map.js_key", "", "高德地图 Web 端 JS API Key（管理端配置）");
        putIfAbsent("map.security_code", "", "高德地图安全密钥 securityJsCode（JS API 2.0 必填，与 Key 配套）");
        putIfAbsent("llm.provider", "deepseek", "LLM 提供商 deepseek/qwen");
        putIfAbsent("llm.api_key", "", "LLM API Key（管理端配置）");
        putIfAbsent("llm.base_url", "https://api.deepseek.com/v1", "LLM 接口地址");
        putIfAbsent("llm.model", "deepseek-chat", "LLM 模型名");
        putIfAbsent("ai.base_url", "http://localhost:8000", "AI 服务地址");
        putIfAbsent("rec.weight_rec", "0.5", "推荐分权重");
        putIfAbsent("rec.weight_quality", "0.3", "质量分权重");
        putIfAbsent("rec.weight_bid", "0.2", "竞价分权重");
    }

    private void putIfAbsent(String key, String value, String desc) {
        Long c = sysConfigMapper.selectCount(new QueryWrapper<SysConfig>().eq("config_key", key));
        if (c == null || c == 0) {
            SysConfig s = new SysConfig();
            s.setConfigKey(key);
            s.setConfigValue(value);
            s.setDescription(desc);
            sysConfigMapper.insert(s);
        }
    }
}
