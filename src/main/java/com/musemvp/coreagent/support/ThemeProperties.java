package com.musemvp.coreagent.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视觉主题配置项，见 DESIGN-FT-002 §4 D-006。
 *
 * <p>开关为**运维级、非用户可见**（D-006/C-05、DD-003）：平台当前无鉴权，不提供任何运行时可写的
 * 主题接口，运维入口为 {@code application.yml} 或环境变量 {@code PLATFORM_THEME_ACTIVE}
 * （Spring 宽松绑定下 {@code platform.theme.active} 对应的标准环境变量名）。
 */
@Component
@ConfigurationProperties(prefix = "platform.theme")
public class ThemeProperties {

    /**
     * 生效主题标识：{@code dark-gold}（默认）｜{@code light}（浅色回退）。
     *
     * <p>取值非法时**不静默回退默认主题**（D-003/C-03）：静默回退会把运维的配置错误藏起来，
     * 全站仍是黑金而无人察觉。非法取值判定为整包失败，接口返回 M01-E007，前端降级到
     * {@code css/tokens.css} 静态基线并 toast 提示——观感一致但失败可见。
     */
    private String active = "dark-gold";

    /** {@code true} 时任一令牌非法即判定整包失败，用于交付前的严格自检（D-003/C-07）。 */
    private boolean strict = false;

    /** 令牌包目录，便于后续外置化；须以 {@code /} 结尾，未以 {@code /} 结尾时由服务端补齐。 */
    private String tokenLocation = "classpath:theme/";

    public String getActive() {
        return active;
    }

    public void setActive(String active) {
        this.active = active;
    }

    public boolean isStrict() {
        return strict;
    }

    public void setStrict(boolean strict) {
        this.strict = strict;
    }

    public String getTokenLocation() {
        return tokenLocation;
    }

    public void setTokenLocation(String tokenLocation) {
        this.tokenLocation = tokenLocation;
    }
}
