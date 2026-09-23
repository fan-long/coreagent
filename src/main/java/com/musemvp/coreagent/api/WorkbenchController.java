package com.musemvp.coreagent.api;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.musemvp.coreagent.api.dto.ComponentCardVO;
import com.musemvp.coreagent.domain.ProjectInfo;
import com.musemvp.coreagent.service.ProjectService;
import com.musemvp.coreagent.service.WorkbenchService;
import com.musemvp.coreagent.support.ApiHeaders;

/**
 * 工作台组件卡片接口，见详细设计 §2.3.2（API-M01-02）。
 */
@RestController
@RequestMapping("/api/workbench")
public class WorkbenchController {

    private final WorkbenchService workbenchService;
    private final ProjectService projectService;

    public WorkbenchController(WorkbenchService workbenchService, ProjectService projectService) {
        this.workbenchService = workbenchService;
        this.projectService = projectService;
    }

    /**
     * 获取当前项目的组件卡片列表。
     *
     * <p>项目上下文经请求头传递并带兜底（§2.4）；平台尚无任何项目时返回空卡片列表而非报错，前端渲染空态，
     * 满足「降级不白屏」（DP-M01-06）。
     */
    @GetMapping("/components")
    public WorkbenchResponse components(
            @RequestHeader(value = ApiHeaders.PROJECT_ID, required = false) String rawProjectId) {
        ProjectInfo project = projectService.resolveCurrent(ApiHeaders.parseProjectId(rawProjectId));
        if (project == null) {
            return new WorkbenchResponse(null, null, List.of());
        }
        return new WorkbenchResponse(project.getId(), project.getName(),
                workbenchService.getCards(project.getId()));
    }

    public record WorkbenchResponse(Long projectId, String projectName, List<ComponentCardVO> components) {
    }
}
