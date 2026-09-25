package com.musemvp.coreagent.support;

import java.util.Arrays;
import java.util.Optional;

/**
 * 令牌适用范围，见 DESIGN-FT-002 §4 D-001/C-02 与 D-002/C-05。
 *
 * <p>本期下发的令牌全部为 {@link #GLOBAL}；全局令牌不得被模块覆盖（D-005/C-03 前端仅接受 GLOBAL）。
 */
public enum TokenScope {

    /** 全局：作用于全平台，模块不得覆盖。 */
    GLOBAL,

    /** 模块：仅作用于声明它的模块。 */
    MODULE,

    /** 界面单元：仅作用于单个界面单元。 */
    UI_UNIT;

    /** 按名解析，大小写不敏感；未知取值返回空，由调用方按「令牌级回退 + 告警」处理。 */
    public static Optional<TokenScope> from(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String normalized = name.trim().toUpperCase(java.util.Locale.ROOT);
        return Arrays.stream(values()).filter(scope -> scope.name().equals(normalized)).findFirst();
    }
}
