package com.waimai.common.exception;

import com.waimai.common.result.R;
import com.waimai.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 唯一键冲突：最典型的是下单幂等（uk_user_client_token）——两个并发请求用同一个
     * clientToken 时只有一个能落库。这不算系统故障，给用户一句能看懂的话，
     * 并提示去订单列表查看（订单其实已经创建成功了）。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public R<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("duplicate key: {}", e.getMessage());
        String msg = String.valueOf(e.getMessage()).contains("uk_user_client_token")
                ? "订单已提交，请勿重复下单，可到「我的订单」查看"
                : "数据已存在，请勿重复提交";
        return R.fail(ResultCode.REPEAT_SUBMIT, msg);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().isEmpty() ? "参数错误"
                : e.getBindingResult().getFieldErrors().get(0).getDefaultMessage();
        return R.fail(ResultCode.PARAM_ERROR, msg);
    }

    /**
     * 404：请求了一个不存在的地址或静态资源（例如 /uploads/xxx.jpg 图片已被删除）。
     * Spring 6.1 起，找不到静态资源抛 NoResourceFoundException、找不到处理器抛
     * NoHandlerFoundException；若落到下面的 Exception 兜底，会返回 HTTP 200 + code 500，
     * 前端和排错的人都会被状态码误导。这里显式返回 HTTP 404，JSON 结构与其他接口保持一致。
     */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<R<Void>> handleNotFound(Exception e) {
        log.warn("not found: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(R.fail(ResultCode.NOT_FOUND, "资源不存在"));
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("system error", e);
        return R.fail(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后再试");
    }
}
