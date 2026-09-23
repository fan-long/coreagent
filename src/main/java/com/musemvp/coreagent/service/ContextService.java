package com.musemvp.coreagent.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.musemvp.coreagent.api.dto.GlobalContextVO;
import com.musemvp.coreagent.api.dto.ProjectVO;
import com.musemvp.coreagent.domain.ProjectInfo;
import com.musemvp.coreagent.support.RoleCatalog;

/**
 * 全局上下文装配，见详细设计 §2.3.1 / §2.4（API-M01-01）。
 *
 * <p>全部上下文为前端会话态，后端不隐式持有用户状态（DP-M01-05）；本类只负责把请求头中的项目标识解析为
 * 真实生效的项目并回传，前端据此纠正本地状态。
 *
 * <p>M02 起阶段的解析与回落统一委托 {@link ProjectStageService}（DD-M02-01）：上下文中的阶段清单
 * 与项目明细接口返回的阶段清单必须同源同口径，避免同一份 {@code project_info.stages} 在不同接口
 * 被解释成不同结果（D-M02-12/P-07）。
 */
@Service
public class ContextService {

    private final ProjectService projectService;
    private final ProjectStageService projectStageService;

    public ContextService(ProjectService projectService, ProjectStageService projectStageService) {
        this.projectService = projectService;
        this.projectStageService = projectStageService;
    }

    /**
     * @param headerProjectId 请求头 {@code X-Project-Id} 解析结果；为空时回退默认项目
     */
    @Transactional(readOnly = true)
    public GlobalContextVO getContext(Long headerProjectId) {
        ProjectInfo currentProject = projectService.resolveCurrent(headerProjectId);
        List<ProjectVO> projects = projectService.list(currentProject == null ? null : currentProject.getId());

        GlobalContextVO.CurrentProject current = currentProject == null
                ? null
                : new GlobalContextVO.CurrentProject(currentProject.getId(), currentProject.getName(),
                        projectStageService.fallback(
                                projectStageService.parse(currentProject.getStages())));

        // 角色不随请求传递（§2.4），后端下发默认值，前端以本地存储中的角色为准
        GlobalContextVO.Role role = RoleCatalog.resolve(null);
        return new GlobalContextVO(current, projects, role, RoleCatalog.roles());
    }
}
