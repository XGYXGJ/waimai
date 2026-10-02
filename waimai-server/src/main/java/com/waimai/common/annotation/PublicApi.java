package com.waimai.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注公开接口：匿名（未登录）也可访问。
 * 与 {@link RequireRole} 不同——RequireRole 是"登录后限定角色"，
 * 本注解是"无需登录即可访问"。
 *
 * 语义：
 *  - 未带 token：直接放行，UserContext 为空（业务代码需对 null userId 兼容）
 *  - 携带 token：正常解析并设置 UserContext，不因本注解跳过登录态
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface PublicApi {
}
