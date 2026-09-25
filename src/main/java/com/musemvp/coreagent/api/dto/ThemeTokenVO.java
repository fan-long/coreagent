package com.musemvp.coreagent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 视觉主题令牌，见 DESIGN-FT-002 §4 D-004/C-02 与 D-001/C-02。
 *
 * <p>对应 DEF-FT002-09 新增业务对象「视觉主题令牌」的七个属性，**不落库**：以版本内资源文件
 * （{@code resources/theme/*.json}）承载，随代码同生命周期发布（DD-001 的取舍论证）。
 *
 * @param group       令牌分组：color｜font｜space｜radius｜border｜shadow｜motion
 * @param name        kebab-case 令牌名称，组内唯一；CSS 自定义属性名为 {@code --ca-} + 该值
 * @param value       令牌取值，形态由分组决定（D-002/C-02）
 * @param description 语义说明（何时使用该令牌）
 * @param scope       适用范围：GLOBAL｜MODULE｜UI_UNIT；本期全部为 GLOBAL
 * @param fallback    是否回退令牌：某项非法时同组令牌以它的取值兜底；每组至少 1 个为 {@code true}
 */
public record ThemeTokenVO(
        String group,
        String name,
        String value,
        String description,
        String scope,
        @JsonInclude(JsonInclude.Include.NON_NULL) Boolean fallback) {

    /** 该令牌对应的 CSS 自定义属性名（D-001/C-03 的名称与变量名映射）。 */
    public String cssVariable() {
        return "--ca-" + name;
    }

    /** 是否为回退令牌；用于「每组至少 1 个回退令牌」的整包校验（D-003/C-04）。 */
    public boolean isFallback() {
        return Boolean.TRUE.equals(fallback);
    }
}
