package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 全局上下文，见详细设计 §2.3.1（API-M01-01）。
 *
 * @param currentProject 当前项目；无任何项目时为 {@code null}。请求头缺失、非法或指向已删除项目时，
 *                       后端已按 §2.4 兜底策略回退为 {@code id} 最小的项目，前端据此纠正本地状态。
 * @param projects       全部项目，排序 {@code updated_at DESC, id DESC}
 * @param role           当前角色，仅用于展示（BR-M01-10）
 * @param roles          角色清单，固定四项
 */
public record GlobalContextVO(
        CurrentProject currentProject,
        List<ProjectVO> projects,
        Role role,
        List<Role> roles) {

    /** 当前项目：较列表项多出工程阶段清单，用于工作台与后续模块的阶段展示。 */
    public record CurrentProject(Long id, String name, List<String> stages) {
    }

    /** 角色展示对象。 */
    public record Role(String code, String name) {
    }
}
