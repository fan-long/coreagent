package com.musemvp.coreagent.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * 令牌包响应，见 DESIGN-FT-002 §4 D-004/C-02。
 *
 * <p>令牌在启动期一次性装载并校验后缓存为不可变列表（{@code List.copyOf}），接口零解析、零 IO、
 * 零数据库访问，P95 目标 &lt; 20ms（D-004 4.5 性能）。
 *
 * @param theme       生效主题标识：dark-gold（默认）｜light（运维回退）
 * @param themeName   主题中文名，供运维排查与页面提示使用
 * @param version     令牌包版本，令牌值变更时递增；前端用于日志与幂等短路
 * @param generatedAt 响应生成时间
 * @param tokens      令牌扁平数组
 * @param warnings    令牌级回退告警；为空时前端不做任何提示
 */
public record ThemeTokenSetVO(
        String theme,
        String themeName,
        String version,
        Instant generatedAt,
        List<ThemeTokenVO> tokens,
        List<ThemeTokenWarningVO> warnings) {

    public ThemeTokenSetVO {
        tokens = tokens == null ? List.of() : List.copyOf(tokens);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
