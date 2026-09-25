package com.musemvp.coreagent.api;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.musemvp.coreagent.api.dto.ThemeTokenSetVO;
import com.musemvp.coreagent.service.ThemeService;

/**
 * 视觉主题令牌接口，见 DESIGN-FT-002 §4 D-004。
 *
 * <p>接口为只读、无参数：**不接收主题标识参数**——生效主题由服务端配置
 * {@code platform.theme.active} 决定，客户端不得指定，防止绕过运维回退开关（DD-003）。
 *
 * <p>不提供任何写接口：令牌随版本发布，落地于 classpath 资源，走代码评审而非运行时写入。
 */
@RestController
@RequestMapping("/api")
public class ThemeController {

    private static final MediaType JSON_UTF8 = MediaType.valueOf("application/json;charset=UTF-8");

    private final ThemeService themeService;

    public ThemeController(ThemeService themeService) {
        this.themeService = themeService;
    }

    /**
     * 下发当前生效主题的令牌包。
     *
     * <p>{@code Cache-Control: no-store} 保证「修改令牌 → 刷新页面 → 全站生效」链路无缓存干扰
     * （AC-FT002-07）。令牌包缺失或非法时 {@link ThemeService#getTokenSet()} 抛 M01-E007
     * （503），由 {@code GlobalExceptionHandler} 统一映射，前端保留静态基线渲染并提示失败。
     */
    @GetMapping("/theme/tokens")
    public ResponseEntity<ThemeTokenSetVO> tokens() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(JSON_UTF8)
                .body(themeService.getTokenSet());
    }
}
