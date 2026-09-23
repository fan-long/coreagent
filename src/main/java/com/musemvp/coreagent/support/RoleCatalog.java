package com.musemvp.coreagent.support;

import java.util.List;

import com.musemvp.coreagent.api.dto.GlobalContextVO;

/**
 * 角色清单。
 *
 * <p>角色仅用于顶栏展示，不约束功能可见性与操作权限（BR-M01-10），因此清单为平台常量而非配置数据。
 * 由后端随 {@code GET /api/context} 下发（§2.3.1），前端不硬编码，便于后续统一维护。
 */
public final class RoleCatalog {

    /** 默认角色：项目架构师。 */
    public static final String DEFAULT_ROLE_CODE = "architect";

    private static final List<GlobalContextVO.Role> ROLES = List.of(
            new GlobalContextVO.Role("architect", "项目架构师"),
            new GlobalContextVO.Role("analyst", "业务分析人员"),
            new GlobalContextVO.Role("reviewer", "评审人员"),
            new GlobalContextVO.Role("auditor", "审计人员"));

    private RoleCatalog() {
    }

    public static List<GlobalContextVO.Role> roles() {
        return ROLES;
    }

    /** 按编码取角色；编码非法时回退默认角色，避免前端 localStorage 中的历史值导致顶栏空白。 */
    public static GlobalContextVO.Role resolve(String code) {
        return ROLES.stream()
                .filter(role -> role.code().equals(code))
                .findFirst()
                .orElseGet(() -> ROLES.stream()
                        .filter(role -> role.code().equals(DEFAULT_ROLE_CODE))
                        .findFirst()
                        .orElseThrow());
    }
}
