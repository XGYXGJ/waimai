package com.waimai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.waimai.common.context.UserContext;
import com.waimai.common.util.RateLimitUtil;
import com.waimai.entity.OperationLog;
import com.waimai.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.Map;

/**
 * 管理端操作日志：记录「谁、什么时候、对哪个对象、做了什么」。
 *
 * <p>写日志失败只告警、不抛异常——审计留痕属于旁路能力，不能因为日志表写不进去
 * 就让审核/退款这些主流程失败。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    /** 记录一条操作日志；detail 超长截断到 500 字 */
    public void record(String action, String targetType, Long targetId, String detail) {
        try {
            OperationLog row = new OperationLog();
            row.setAdminId(UserContext.userId());
            row.setAction(action);
            row.setTargetType(targetType);
            row.setTargetId(targetId);
            row.setDetail(detail == null ? null
                    : detail.substring(0, Math.min(detail.length(), 500)));
            row.setIp(currentIp());
            operationLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("写操作日志失败 action={} target={}#{}", action, targetType, targetId, e);
        }
    }

    /** 分页查询（管理端），可按动作码过滤 */
    public Map<String, Object> page(String action, int page, int size) {
        QueryWrapper<OperationLog> qw = new QueryWrapper<OperationLog>()
                .eq(action != null && !action.isBlank(), "action", action)
                .orderByDesc("created_at").orderByDesc("id");
        long total = operationLogMapper.selectCount(qw);
        List<OperationLog> records = operationLogMapper.selectList(
                qw.last("limit " + ((page - 1) * size) + "," + size));
        return Map.of("total", total, "records", records);
    }

    private String currentIp() {
        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : RateLimitUtil.clientIp(attrs.getRequest());
    }
}
