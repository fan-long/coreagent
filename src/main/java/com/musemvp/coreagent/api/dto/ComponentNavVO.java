package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 组件信息二级导航，见详细设计 §2.3.3（API-M01-03）与 UI-M01-04。
 *
 * <p>组件不存在任何工程仓库时按 BR-M01-04 降级：{@code repositories} 为空数组、
 * {@code currentRepositoryId} 为 {@code null}，{@code groups} 仅保留不依赖工程的菜单项。
 *
 * @param currentRepositoryId 当前工程仓库；缺省取该组件默认工程
 */
public record ComponentNavVO(
        ComponentBrief component,
        List<RepositoryVO> repositories,
        String currentRepositoryId,
        List<NavGroupVO> groups) {

    public record ComponentBrief(String id, String name, String icon, int repositoryCount) {
    }
}
