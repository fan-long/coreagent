package com.musemvp.coreagent.support;

import java.util.Arrays;
import java.util.Optional;

/**
 * 视觉主题令牌分组，见 DESIGN-FT-002 §4 D-001/C-04。
 *
 * <p>七组与 DEF-FT002-10「视觉主题令牌 · 令牌分组」的枚举一一对应，组键即令牌包 JSON 中
 * {@code tokens[].group} 的取值，也是前端注入时的分组白名单来源。
 */
public enum TokenGroup {

    /** 色板：背景、描边、文字、强调、语义状态、图表。 */
    COLOR("color"),

    /** 字体：字族、字号、字重、行高、数字特性。 */
    FONT("font"),

    /** 间距：元素内外间距梯度（4px 基数）。 */
    SPACE("space"),

    /** 圆角：控件、卡片、弹窗、胶囊。 */
    RADIUS("radius"),

    /** 描边：线宽与线型。 */
    BORDER("border"),

    /** 阴影：卡片、抬升面、弹窗、金色辉光。 */
    SHADOW("shadow"),

    /** 动效：时长与缓动曲线。 */
    MOTION("motion");

    private final String key;

    TokenGroup(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    /** 按组键解析；未知组键返回空，由调用方按「令牌级回退 + 告警」处理（D-002/C-01）。 */
    public static Optional<TokenGroup> from(String key) {
        return Arrays.stream(values()).filter(group -> group.key.equals(key)).findFirst();
    }
}
