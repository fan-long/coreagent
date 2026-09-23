package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 导航分组，见详细设计 §2.3.3。
 *
 * @param key   分组标识：{@code functions} 功能菜单 / {@code assets} 组件公共资产
 * @param name  分组标题；{@code null} 表示该分组不渲染标题（功能菜单直接平铺在工程选择器下方）
 * @param items 分组内菜单项
 */
public record NavGroupVO(String key, String name, List<NavItemVO> items) {
}
