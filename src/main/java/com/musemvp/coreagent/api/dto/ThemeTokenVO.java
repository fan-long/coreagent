package com.musemvp.coreagent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 视觉主题令牌，见详细设计 §3.3.1 与 §5.2。
 *
 * <p>对应原始需求 §8 新增业务对象「视觉主题令牌」的七个属性，**不落库**：以版本内资源文件
 * （{@code resources/theme/*.json}）承载，随代码同生命周期发布（§5.3 论证）。
 *
 * @param group       令牌分组：color｜font｜space｜radius｜border｜shadow｜motion
 * @param name        kebab-case 令牌名称，组内唯一；CSS 自定义属性名为 {@code --ca-} + 该值
 * @param value       令牌取值，形态由分组决定（§2.1）
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

    /** 该令牌对应的 CSS 自定义属性名（§2.1 命名规范）。 */
    public String cssVariable() {
        return "--ca-" + name;
    }

    /** 是否为回退令牌；用于「每组至少 1 个回退令牌」的整包校验（§3.4.2）。 */
    public boolean isFallback() {
        return Boolean.TRUE.equals(fallback);
    }
}
