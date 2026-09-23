package com.musemvp.coreagent.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.musemvp.coreagent.api.dto.ComponentNavVO;
import com.musemvp.coreagent.api.dto.NavGroupVO;
import com.musemvp.coreagent.api.dto.NavItemVO;
import com.musemvp.coreagent.api.dto.RepositoryVO;
import com.musemvp.coreagent.domain.ComponentDefinition;
import com.musemvp.coreagent.domain.ComponentRepository;
import com.musemvp.coreagent.domain.SpecGroupDefinition;
import com.musemvp.coreagent.domain.TaskType;
import com.musemvp.coreagent.domain.TechFramework;
import com.musemvp.coreagent.repository.ComponentDefinitionRepository;
import com.musemvp.coreagent.repository.ComponentRepositoryRepository;
import com.musemvp.coreagent.repository.IssueTrackingRepository;
import com.musemvp.coreagent.repository.SpecGroupRepository;
import com.musemvp.coreagent.repository.TaskInstanceRepository;
import com.musemvp.coreagent.repository.TaskTypeRepository;
import com.musemvp.coreagent.repository.TechFrameworkRepository;
import com.musemvp.coreagent.support.BizException;
import com.musemvp.coreagent.support.ErrorCode;
import com.musemvp.coreagent.support.IssueStatus;

/**
 * 组件信息二级导航装配，见详细设计 §2.5.2（CAP-M01-05）。
 *
 * <p>菜单是否可用由本类判定并下发 {@code enabled} + {@code disabledReason}，前端只渲染不判断，保证多入口
 * 一致（DP-M01-03）；菜单以描述符下发，新增菜单不改前端渲染逻辑（DP-M01-04）。
 */
@Service
public class NavigationService {

    private static final String GROUP_FUNCTIONS = "functions";
    private static final String GROUP_ASSETS = "assets";
    private static final String ASSETS_GROUP_NAME = "组件公共资产";

    private static final String CONFIG_PATH_COMPONENT = "环境管理 / 项目配置 / 应用组件";
    private static final String CONFIG_PATH_FRAMEWORK = "环境管理 / 项目配置 / 技术框架";
    private static final String CONFIG_PATH_SPEC_TEMPLATE = "环境管理 / 模板管理 / Spec模板";

    private static final String DEFAULT_REQUIREMENT_MENU_NAME = "需求文档";
    private static final String SUFFIX_INTERFACE = "接口设计";
    private static final String SUFFIX_DATA_MODEL = "数据模型";
    private static final String SUFFIX_TEST_CASE = "测试案例";

    private final ComponentDefinitionRepository componentDefinitionRepository;
    private final ComponentRepositoryRepository componentRepositoryRepository;
    private final SpecGroupRepository specGroupRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final TechFrameworkRepository techFrameworkRepository;
    private final IssueTrackingRepository issueTrackingRepository;
    private final TaskInstanceRepository taskInstanceRepository;

    public NavigationService(ComponentDefinitionRepository componentDefinitionRepository,
                             ComponentRepositoryRepository componentRepositoryRepository,
                             SpecGroupRepository specGroupRepository,
                             TaskTypeRepository taskTypeRepository,
                             TechFrameworkRepository techFrameworkRepository,
                             IssueTrackingRepository issueTrackingRepository,
                             TaskInstanceRepository taskInstanceRepository) {
        this.componentDefinitionRepository = componentDefinitionRepository;
        this.componentRepositoryRepository = componentRepositoryRepository;
        this.specGroupRepository = specGroupRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.techFrameworkRepository = techFrameworkRepository;
        this.issueTrackingRepository = issueTrackingRepository;
        this.taskInstanceRepository = taskInstanceRepository;
    }

    /**
     * 装配组件信息二级导航。
     *
     * @param componentId  应用组件标识
     * @param repositoryId 当前工程仓库；缺省取该组件默认工程
     * @throws BizException M01-E004 组件不存在；M01-E005 工程仓库不属于该组件
     */
    @Transactional(readOnly = true)
    public ComponentNavVO getComponentNav(String componentId, String repositoryId) {
        // 步骤 1：校验组件存在
        ComponentDefinition component = componentDefinitionRepository.findById(componentId)
                .orElseThrow(() -> new BizException(ErrorCode.M01_E004));

        // 步骤 2：工程仓库与当前工程
        List<ComponentRepository> repositories = componentRepositoryRepository.findAllOrdered(componentId);
        ComponentRepository currentRepository = RepositorySelection.currentOf(repositories, repositoryId);

        // 步骤 3：组件配置（规格组名 + 主任务类型 + 三类 Agent）
        SpecGroupDefinition specGroup = component.getSpecGroupId() == null
                ? null
                : specGroupRepository.findById(component.getSpecGroupId()).orElse(null);
        TaskType mainTaskType = resolveMainTaskType(component);

        // 步骤 4：当前工程的框架能力
        Map<String, TechFramework> frameworks = loadFrameworks(currentRepository);
        boolean orchestrationSupported = supports(frameworks, currentRepository == null
                ? null : currentRepository.getAppFramework(), FrameworkCapability.ORCHESTRATION);
        boolean ruleLibSupported = supports(frameworks, currentRepository == null
                ? null : currentRepository.getBaseFramework(), FrameworkCapability.RULE_LIB);

        // 步骤 5：按命名规则与可用性判定规则生成菜单树
        List<NavGroupVO> groups = new ArrayList<>(2);
        if (currentRepository != null) {
            groups.add(new NavGroupVO(GROUP_FUNCTIONS, null,
                    functionItems(component.getId(), currentRepository, mainTaskType,
                            orchestrationSupported, ruleLibSupported)));
        }
        // 步骤 6：工程仓库为空时按 BR-M01-04 降级——只下发不依赖工程的组件级菜单
        groups.add(new NavGroupVO(GROUP_ASSETS, ASSETS_GROUP_NAME,
                assetItems(component.getId(), specGroup == null ? null : specGroup.getName(),
                        mainTaskType)));

        return new ComponentNavVO(
                new ComponentNavVO.ComponentBrief(component.getId(), component.getName(),
                        component.getIcon(), repositories.size()),
                repositories.stream().map(NavigationService::toRepositoryVO).toList(),
                currentRepository == null ? null : currentRepository.getId(),
                groups);
    }

    /** 功能菜单：全部依赖当前工程，无工程仓库时整体不下发（BR-M01-04）。 */
    private List<NavItemVO> functionItems(String componentId,
                                          ComponentRepository currentRepository,
                                          TaskType mainTaskType,
                                          boolean orchestrationSupported,
                                          boolean ruleLibSupported) {
        String repositoryId = currentRepository.getId();
        String mainTaskTypeCode = mainTaskType == null ? null : mainTaskType.getCode();

        Long taskBadge = StringUtils.hasText(mainTaskTypeCode)
                ? taskInstanceRepository.countByRepositoryAndTaskType(repositoryId, mainTaskTypeCode)
                : null;
        Long issueBadge = issueTrackingRepository.countByRepositoryAndStatus(repositoryId, IssueStatus.OPEN);

        List<NavItemVO> items = new ArrayList<>(5);
        items.add(NavItemVO.fixed("task", "任务管理", route(componentId, "task"), true, taskBadge, null, null));
        items.add(NavItemVO.fixed("orchestration", "服务编排全景", route(componentId, "orchestration"),
                orchestrationSupported,
                null,
                orchestrationSupported ? null : "当前工程的应用开发框架不支持编排模式",
                orchestrationSupported ? null : CONFIG_PATH_FRAMEWORK));
        items.add(NavItemVO.fixed("rulelib", "规则库全景", route(componentId, "rulelib"),
                ruleLibSupported,
                null,
                ruleLibSupported ? null : "当前工程的基础技术框架不支持规则库模式",
                ruleLibSupported ? null : CONFIG_PATH_FRAMEWORK));
        items.add(NavItemVO.fixed("issue", "问题跟踪", route(componentId, "issue"), true, issueBadge, null, null));
        items.add(NavItemVO.fixed("code", "代码库", route(componentId, "code"), true, null, null, null));
        return items;
    }

    /**
     * 组件公共资产：不依赖当前工程，工程仓库为空时仍下发（BR-M01-04）。
     *
     * <p>层级按 PQ-06 的当前结论实现——「设计资产」「测试资产」为可折叠父项，分别下挂「接口设计／数据模型」
     * 与「测试案例」；若结论调整，只需修改本方法的组装，前端渲染逻辑不变（DP-M01-04）。
     */
    private List<NavItemVO> assetItems(String componentId, String specGroupName, TaskType mainTaskType) {
        String taskTypeName = mainTaskType == null ? null : mainTaskType.getName();
        boolean agentConfigured = hasAgentConfigured(mainTaskType);

        List<NavItemVO> items = new ArrayList<>(4);
        items.add(NavItemVO.named("requirement",
                specGroupName == null ? DEFAULT_REQUIREMENT_MENU_NAME : specGroupName,
                NavItemVO.NameSource.SPEC_GROUP,
                route(componentId, "requirement"), true, null, null, null));

        items.add(NavItemVO.group("design", "设计资产", List.of(
                agentGatedItem("interface", taskTypeName, SUFFIX_INTERFACE, componentId, agentConfigured),
                agentGatedItem("datamodel", taskTypeName, SUFFIX_DATA_MODEL, componentId, agentConfigured))));

        // 测试案例菜单只受命名规则约束，无可用性判定条件（§2.3.3 可用性判定表「其余恒可用」）
        items.add(NavItemVO.group("test", "测试资产", List.of(
                NavItemVO.named("testcase", namedWithSuffix(taskTypeName, SUFFIX_TEST_CASE),
                        NavItemVO.NameSource.MAIN_TASK_TYPE, route(componentId, "testcase"),
                        true, null, null, null))));

        items.add(NavItemVO.fixed("knowledge", "知识库", route(componentId, "knowledge"), true, null, null, null));
        return items;
    }

    /**
     * 命名规则 BR-M01-06：{@code task_type.name} + 后缀，未设主任务类型时取默认名（仅后缀）。
     *
     * <p>可用性判定 BR-M01-07：主任务类型对应的 {@code task_type} 已配置 Agent 时可用。
     */
    private NavItemVO agentGatedItem(String key, String taskTypeName, String nameSuffix,
                                     String componentId, boolean agentConfigured) {
        String name = namedWithSuffix(taskTypeName, nameSuffix);
        if (agentConfigured) {
            return NavItemVO.named(key, name, NavItemVO.NameSource.MAIN_TASK_TYPE,
                    route(componentId, key), true, null, null, null);
        }
        boolean mainTaskTypeMissing = taskTypeName == null;
        return NavItemVO.named(key, name, NavItemVO.NameSource.MAIN_TASK_TYPE, route(componentId, key), false, null,
                mainTaskTypeMissing ? "未设置主任务类型" : "任务类型「" + taskTypeName + "」未配置 Agent",
                mainTaskTypeMissing ? CONFIG_PATH_COMPONENT : CONFIG_PATH_SPEC_TEMPLATE);
    }

    /** BR-M01-06 命名规则：{@code task_type.name} + 后缀；未设主任务类型时取默认名（即后缀本身）。 */
    private static String namedWithSuffix(String taskTypeName, String nameSuffix) {
        return taskTypeName == null ? nameSuffix : taskTypeName + nameSuffix;
    }

    /** 主任务类型记录；未关联规格组或未设主任务类型时为空。 */
    private TaskType resolveMainTaskType(ComponentDefinition component) {
        if (component.getSpecGroupId() == null || !StringUtils.hasText(component.getMainTaskType())) {
            return null;
        }
        return taskTypeRepository
                .findBySpecGroupIdAndCode(component.getSpecGroupId(), component.getMainTaskType())
                .orElse(null);
    }

    /** BR-M01-07 判定口径：PQ-03 结论前采用「{@code assist_agent} 非空即视为已配置」。口径调整只改此处。 */
    private boolean hasAgentConfigured(TaskType mainTaskType) {
        return mainTaskType != null && StringUtils.hasText(mainTaskType.getAssistAgent());
    }

    private Map<String, TechFramework> loadFrameworks(ComponentRepository currentRepository) {
        if (currentRepository == null) {
            return Map.of();
        }
        List<String> frameworkIds = new ArrayList<>(2);
        if (StringUtils.hasText(currentRepository.getAppFramework())) {
            frameworkIds.add(currentRepository.getAppFramework());
        }
        if (StringUtils.hasText(currentRepository.getBaseFramework())) {
            frameworkIds.add(currentRepository.getBaseFramework());
        }
        if (frameworkIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TechFramework> frameworks = new HashMap<>();
        for (TechFramework framework : techFrameworkRepository.findAllById(frameworkIds)) {
            frameworks.put(framework.getId(), framework);
        }
        return frameworks;
    }

    /** 框架能力开关判定：框架缺失或标识为空一律视为不支持（BR-M01-08、BR-M01-09）。 */
    private boolean supports(Map<String, TechFramework> frameworks, String frameworkId,
                             FrameworkCapability capability) {
        if (!StringUtils.hasText(frameworkId)) {
            return false;
        }
        TechFramework framework = frameworks.get(frameworkId);
        if (framework == null) {
            return false;
        }
        return switch (capability) {
            case ORCHESTRATION -> Boolean.TRUE.equals(framework.getOrches());
            case RULE_LIB -> Boolean.TRUE.equals(framework.getRulelib());
        };
    }

    private static RepositoryVO toRepositoryVO(ComponentRepository repository) {
        return new RepositoryVO(repository.getId(), repository.getName(), repository.getVersion(),
                repository.getBranch(), Boolean.TRUE.equals(repository.getIsDefault()));
    }

    private static String route(String componentId, String menuKey) {
        return "#/component/" + componentId + "/" + menuKey;
    }

    private enum FrameworkCapability {
        ORCHESTRATION,
        RULE_LIB
    }
}
