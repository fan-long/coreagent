package com.musemvp.coreagent.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.musemvp.coreagent.api.dto.ComponentNavVO;
import com.musemvp.coreagent.service.NavigationService;

/**
 * 组件信息二级导航接口，见详细设计 §2.3.3（API-M01-03）。
 *
 * <p>本接口不接收项目上下文：{@code component_definition.id} 已全局唯一，组件标识本身即可确定导航上下文，
 * 项目仅用于前端工作台与环境管理视图的取数范围。
 */
@RestController
@RequestMapping("/api/navigation")
public class NavigationController {

    private final NavigationService navigationService;

    public NavigationController(NavigationService navigationService) {
        this.navigationService = navigationService;
    }

    /**
     * 获取组件信息二级导航（工程仓库 + 菜单命名 + 可用性）。
     *
     * @param componentId  应用组件标识
     * @param repositoryId 当前工程仓库；缺省取该组件默认工程
     */
    @GetMapping("/component")
    public ComponentNavVO component(@RequestParam("componentId") String componentId,
                                    @RequestParam(value = "repositoryId", required = false) String repositoryId) {
        return navigationService.getComponentNav(componentId, repositoryId);
    }
}
