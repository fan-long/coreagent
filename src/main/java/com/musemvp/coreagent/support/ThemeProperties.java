package com.musemvp.coreagent.support;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 视觉主题配置项，见详细设计 §3.6。
 *
 * <p>开关为**运维级、非用户可见**：平台当前无鉴权（§3.2），不提供任何运行时可写的主题接口，
 * 运维入口为 {@code application.yml} 或环境变量 {@code PLATFORM_THEME}。
 */
@Component
@ConfigurationProperties(prefix = "platform.theme")
public class ThemeProperties {

    /** 生效主题标识：{@code dark-gold}（默认）｜{@code light}（浅色回退）。取值非法时回退为默认主题。 */
    private String active = "dark-gold";

    /** {@code true} 时任一令牌非法即判定整包失败，用于交付前的严格自检（§3.6）。 */
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
