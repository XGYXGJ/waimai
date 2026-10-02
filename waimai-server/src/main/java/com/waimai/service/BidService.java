package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.exception.BizException;
import com.waimai.dto.WebDTO;
import com.waimai.entity.BidCampaign;
import com.waimai.entity.BidLog;
import com.waimai.entity.Merchant;
import com.waimai.mapper.BidCampaignMapper;
import com.waimai.mapper.BidLogMapper;
import com.waimai.mapper.MerchantMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 竞价排名：商户投放广告位，搜索/列表页按 eCPM（出价×质量分）插入广告位。
 * 广告位固定插入列表第 1/4/7 位，带"广告"标识。
 */
@Service
@RequiredArgsConstructor
public class BidService {

    private final BidCampaignMapper campaignMapper;
    private final BidLogMapper bidLogMapper;
    private final MerchantMapper merchantMapper;

    /* ---------- 商户端：投放管理 ---------- */

    private Long merchantIdOf(Long merchantUserId) {
        Merchant m = merchantMapper.selectOne(new QueryWrapper<Merchant>().eq("user_id", merchantUserId));
        if (m == null) throw new BizException("非商户账号");
        return m.getId();
    }

    public List<BidCampaign> myCampaigns(Long merchantUserId) {
        Long merchantId = merchantIdOf(merchantUserId);
        return campaignMapper.selectList(new QueryWrapper<BidCampaign>()
                .eq("merchant_id", merchantId).orderByDesc("created_at"));
    }

    public void createCampaign(Long merchantUserId, WebDTO.BidCampaignReq req) {
        Long merchantId = merchantIdOf(merchantUserId);
        if (req.getKeyword() == null || req.getKeyword().isBlank()) throw new BizException("关键词不能为空");
        if (req.getBid() == null || req.getBid() <= 0) throw new BizException("出价必须大于0");
        BidCampaign c = new BidCampaign();
        c.setMerchantId(merchantId);
        c.setKeyword(req.getKeyword());
        c.setBid(BigDecimal.valueOf(req.getBid()));
        c.setDailyBudget(req.getDailyBudget() == null ? BigDecimal.valueOf(100) : BigDecimal.valueOf(req.getDailyBudget()));
        c.setTodaySpent(BigDecimal.ZERO);
        c.setStatus(1);
        c.setStartDate(req.getStartDate() == null ? LocalDate.now() : LocalDate.parse(req.getStartDate()));
        c.setEndDate(req.getEndDate() == null ? LocalDate.now().plusDays(30) : LocalDate.parse(req.getEndDate()));
        campaignMapper.insert(c);
    }

    public void updateStatus(Long merchantUserId, Long id, Integer status) {
        Long merchantId = merchantIdOf(merchantUserId);
        BidCampaign c = campaignMapper.selectById(id);
        if (c == null || !c.getMerchantId().equals(merchantId)) throw new BizException("投放计划不存在");
        c.setStatus(status);
        campaignMapper.updateById(c);
    }

    /* ---------- 用户端/列表页：取广告位 ---------- */

    /**
     * 根据关键词取生效中的竞价广告（按 eCPM 降序）。
     * eCPM = 出价 × 质量分；质量分 = 0.4×评分 + 0.3×销量归一 + 0.3×履约率。
     * 返回：campaignId + merchantId + bid + ecpm（供列表页插入广告位）。
     */
    public List<Map<String, Object>> adsForKeyword(String keyword, Double lng, Double lat, Long userId) {
        if (keyword == null || keyword.isBlank()) return List.of();
        List<BidCampaign> list = campaignMapper.selectList(new QueryWrapper<BidCampaign>()
                .eq("status", 1)
                .eq("keyword", keyword)
                .ge("end_date", LocalDate.now()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (BidCampaign c : list) {
            Merchant m = merchantMapper.selectById(c.getMerchantId());
            if (m == null || m.getAuditStatus() != 1 || m.getOpenStatus() != 1) continue;
            // 当日预算控制
            if (c.getTodaySpent().compareTo(c.getDailyBudget()) >= 0) continue;
            double rating = m.getRating() == null ? 4.5 : m.getRating().doubleValue();
            double salesNorm = m.getMonthlySales() == null ? 0 : Math.min(1.0, m.getMonthlySales() / 1000.0);
            double quality = 0.4 * (rating / 5.0) + 0.3 * salesNorm + 0.3 * 0.95; // 履约率简化 0.95
            double ecpm = c.getBid().doubleValue() * quality;

            Map<String, Object> m2 = new LinkedHashMap<>();
            m2.put("campaignId", c.getId());
            m2.put("merchantId", c.getMerchantId());
            m2.put("bid", c.getBid());
            m2.put("ecpm", ecpm);
            result.add(m2);
        }
        result.sort((a, b) -> Double.compare((Double) b.get("ecpm"), (Double) a.get("ecpm")));
        return result;
    }

    /** 记录一次广告曝光/点击（计费）。CPC 计费：点击时扣费，费用=出价。 */
    public void log(Long campaignId, Long userId, Integer position, Integer clicked) {
        BidCampaign c = campaignMapper.selectById(campaignId);
        if (c == null) return;
        BigDecimal cost = (clicked != null && clicked == 1) ? c.getBid() : BigDecimal.ZERO;
        if (cost.compareTo(BigDecimal.ZERO) > 0) {
            // 预算控制
            if (c.getTodaySpent().add(cost).compareTo(c.getDailyBudget()) > 0) return;
            c.setTodaySpent(c.getTodaySpent().add(cost));
            campaignMapper.updateById(c);
        }
        BidLog log = new BidLog();
        log.setCampaignId(campaignId);
        log.setUserId(userId);
        log.setPosition(position);
        log.setClicked(clicked == null ? 0 : clicked);
        log.setCost(cost);
        bidLogMapper.insert(log);
    }
}
