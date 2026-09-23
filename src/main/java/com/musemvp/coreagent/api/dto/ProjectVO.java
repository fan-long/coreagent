package com.musemvp.coreagent.api.dto;

/**
 * 项目列表项，见详细设计 §2.3.1 / §2.3.4。
 *
 * <p>{@code current} 为 {@code true} 时前端渲染「当前」徽标且不渲染切换按钮（UI-M01-06）。
 */
public record ProjectVO(Long id, String name, boolean current) {
}
