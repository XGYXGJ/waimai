package com.waimai.common.result;

public class ResultCode {
    public static final int PARAM_ERROR = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int SYSTEM_ERROR = 500;

    // 业务错误码 4xxxx
    public static final int BIZ_ERROR = 40000;
    public static final int STOCK_NOT_ENOUGH = 40001;
    public static final int SHOP_CLOSED = 40002;
    public static final int COUPON_INVALID = 40003;
    public static final int ORDER_STATUS_CHANGED = 40010;
    public static final int GRAB_FAILED = 40011;
    public static final int CART_MERCHANT_CONFLICT = 40012;
    public static final int REPEAT_SUBMIT = 40013;
    /** 收货地址超出商家配送范围 */
    public static final int OUT_OF_DELIVERY_RANGE = 40014;
}
