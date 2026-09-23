package com.musemvp.coreagent.api.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 导航项描述符，见详细设计 §2.3.3。
 *
 * <p>菜单以描述符下发，新增菜单不改前端渲染逻辑（DP-M01-04）。可用性由后端判定并下发
 * {@code enabled} + {@code disabledReason}，前端只渲染不判断（DP-M01-03）。
 *
 * @param key            菜单标识，前端路由与选中态以此为准
 * @param name           菜单名称，可能随组件配置动态变化
 * @param nameSource     命名来源；仅动态命名的菜单项下发，前端不参与判定
 * @param route          hash 路由；父级分组项为 {@code null}（不可点击，仅折叠）
 * @param enabled        是否可用；{@code false} 时前端置灰且阻止点击
 * @param badge          数量角标；{@code null} 表示不展示角标
 * @param disabledReason 不可用原因，悬停提示用
 * @param configPath     前置配置位置，与 {@code disabledReason} 一同提示
 * @param children       子菜单；{@code null} 表示为叶子菜单
 */
public record NavItemVO(
        String key,
        String name,
        @JsonInclude(JsonInclude.Include.NON_NULL) NameSource nameSource,
        String route,
        boolean enabled,
        Long badge,
        @JsonInclude(JsonInclude.Include.NON_NULL) String disabledReason,
        @JsonInclude(JsonInclude.Include.NON_NULL) String configPath,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<NavItemVO> children) {

    /** 菜单命名来源，见 §2.3.3 菜单命名规则表。 */
    public enum NameSource {
        /** 取组件关联的规格组名称，未关联时用默认名。 */
        SPEC_GROUP,
        /** 取组件主任务类型名称拼接后缀，未设置主任务类型时用默认名。 */
        MAIN_TASK_TYPE,
        /** 固定名称。 */
        FIXED
    }

    /** 固定命名菜单项。 */
    public static NavItemVO fixed(String key, String name, String route, boolean enabled, Long badge,
                                  String disabledReason, String configPath) {
        return new NavItemVO(key, name, NameSource.FIXED, route, enabled, badge,
                disabledReason, configPath, null);
    }

    /** 动态命名菜单项（SPEC_GROUP / MAIN_TASK_TYPE）。 */
    public static NavItemVO named(String key, String name, NameSource nameSource, String route,
                                  boolean enabled, Long badge, String disabledReason, String configPath) {
        return new NavItemVO(key, name, nameSource, route, enabled, badge,
                disabledReason, configPath, null);
    }

    /** 可折叠父项：自身不可点击，仅承载子菜单。 */
    public static NavItemVO group(String key, String name, List<NavItemVO> children) {
        return new NavItemVO(key, name, NameSource.FIXED, null, true, null, null, null, children);
    }
}
