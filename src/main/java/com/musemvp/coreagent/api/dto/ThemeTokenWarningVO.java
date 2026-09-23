package com.musemvp.coreagent.api.dto;

/**
 * 令牌级回退告警，见详细设计 §2.3 级别③与 §3.3.1。
 *
 * <p>单个令牌非法**不返回错误**：降级为该令牌的回退值并在响应的 {@code warnings} 中列出，其余令牌正常生效，
 * 页面不失败（ft-FT-002-02 异常预期）。整包级失败才返回 M01-E007。
 *
 * @param group         令牌所属分组；分组本身非法时为原始取值
 * @param name          令牌名称
 * @param reason        非法原因码，见 {@code TokenValidator} 的 REASON_* 常量
 * @param fallbackValue 实际采用的回退取值；令牌被整条丢弃时为 {@code null}
 */
public record ThemeTokenWarningVO(String group, String name, String reason, String fallbackValue) {

    /** 令牌无法回退、被整条丢弃（如分组未知、回退令牌缺失）时的告警。 */
    public static ThemeTokenWarningVO dropped(String group, String name, String reason) {
        return new ThemeTokenWarningVO(group, name, reason, null);
    }
}
