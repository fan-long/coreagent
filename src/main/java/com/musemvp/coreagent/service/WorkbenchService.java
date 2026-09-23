package com.musemvp.coreagent.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.musemvp.coreagent.api.dto.ComponentCardVO;
import com.musemvp.coreagent.api.dto.RepositoryVO;
import com.musemvp.coreagent.domain.ComponentRepository;
import com.musemvp.coreagent.domain.TaskType;
import com.musemvp.coreagent.repository.ComponentDefinitionRepository;
import com.musemvp.coreagent.repository.ComponentDefinitionRepository.WorkbenchComponentRow;
import com.musemvp.coreagent.repository.ComponentRepositoryRepository;
import com.musemvp.coreagent.repository.IssueTrackingRepository;
import com.musemvp.coreagent.repository.IssueTrackingRepository.RepositoryCount;
import com.musemvp.coreagent.repository.TaskInstanceRepository;
import com.musemvp.coreagent.repository.TaskInstanceRepository.TaskTypeCount;
import com.musemvp.coreagent.repository.TaskTypeRepository;
import com.musemvp.coreagent.support.ComponentStatus;
import com.musemvp.coreagent.support.IssueStatus;

/**
 * 工作台组件卡片装配，见详细设计 §2.5.1（CAP-M01-01）。
 *
 * <p>三项指标与排序规则只在本类实现，避免工作台与组件列表口径漂移（DP-M01-02）。卡片为纯派生视图，
 * 不新增业务表、不落库（DP-M01-01）。
 */
@Service
public class WorkbenchService {

    private static final String DEFAULT_REPOSITORY_UNAVAILABLE_REASON = "默认工程不可用";
    private static final String RULE_LIB_LABEL = "规则库";

    /**
     * PQ-01：数据模型 v1（43 表）中不存在规则库相关业务表，「规则数量」无取数来源。在口径确认前一律返回
     * {@code value = 0} 且 {@code available = false}，前端以占位展示并附悬停说明。
     */
    private static final String RULE_LIB_UNAVAILABLE_REASON = "规则库口径数据源未就绪";

    private final ComponentDefinitionRepository componentDefinitionRepository;
    private final ComponentRepositoryRepository componentRepositoryRepository;
    private final TaskTypeRepository taskTypeRepository;
    private final IssueTrackingRepository issueTrackingRepository;
    private final TaskInstanceRepository taskInstanceRepository;

    public WorkbenchService(ComponentDefinitionRepository componentDefinitionRepository,
                            ComponentRepositoryRepository componentRepositoryRepository,
                            TaskTypeRepository taskTypeRepository,
                            IssueTrackingRepository issueTrackingRepository,
                            TaskInstanceRepository taskInstanceRepository) {
        this.componentDefinitionRepository = componentDefinitionRepository;
        this.componentRepositoryRepository = componentRepositoryRepository;
        this.taskTypeRepository = taskTypeRepository;
        this.issueTrackingRepository = issueTrackingRepository;
        this.taskInstanceRepository = taskInstanceRepository;
    }

    /**
     * 装配指定项目的组件卡片列表。
     *
     * <p>固定 4 次数据库往返（组件行、工程仓库、问题计数、任务计数）加一次主任务类型名称查询，与组件数量
     * 无关，避免逐组件查询的 N+1（§2.6.2）。
     *
     * @param projectId 当前项目id
     * @return 启用在前、未启用在后的卡片列表；项目下无组件时返回空列表
     */
    @Transactional(readOnly = true)
    public List<ComponentCardVO> getCards(Long projectId) {
        // 步骤 1：组件 + 规格组名，已按 BR-M01-01 排序
        List<WorkbenchComponentRow> rows =
                componentDefinitionRepository.findWorkbenchRows(projectId, ComponentStatus.ENABLED);
        if (rows.isEmpty()) {
            return List.of();
        }

        // 步骤 2：工程仓库分布，内存中按组件分组
        List<String> componentIds = rows.stream().map(WorkbenchComponentRow::getId).toList();
        Map<String, List<ComponentRepository>> repositoriesByComponent = new HashMap<>();
        for (ComponentRepository repository : componentRepositoryRepository.findByComponentIdIn(componentIds)) {
            repositoriesByComponent.computeIfAbsent(repository.getComponentId(), key -> new ArrayList<>())
                    .add(repository);
        }

        Map<String, ComponentRepository> defaultRepositories = new HashMap<>();
        repositoriesByComponent.forEach((componentId, repositories) ->
                RepositorySelection.defaultOf(repositories)
                        .ifPresent(repository -> defaultRepositories.put(componentId, repository)));
        List<String> defaultRepositoryIds = defaultRepositories.values().stream()
                .map(ComponentRepository::getId)
                .toList();

        // 步骤 3：阻塞事项数（按默认工程聚合）
        Map<String, Long> blockingIssues = countBlockingIssues(defaultRepositoryIds);

        // 步骤 4：任务数量（按默认工程 + 主任务类型聚合）
        List<String> mainTaskTypes = rows.stream()
                .map(WorkbenchComponentRow::getMainTaskType)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        Map<String, Map<String, Long>> taskCounts = countTasks(defaultRepositoryIds, mainTaskTypes);

        // 步骤 5：主任务类型名称
        Map<String, String> taskTypeNames = loadTaskTypeNames(rows);

        // 步骤 6：组装卡片，未启用组件保留并置 enabled = false（BR-M01-02）
        List<ComponentCardVO> cards = new ArrayList<>(rows.size());
        for (WorkbenchComponentRow row : rows) {
            cards.add(toCard(row,
                    defaultRepositories.get(row.getId()),
                    repositoriesByComponent.getOrDefault(row.getId(), List.of()).size(),
                    blockingIssues,
                    taskCounts,
                    taskTypeNames));
        }
        return cards;
    }

    private Map<String, Long> countBlockingIssues(List<String> defaultRepositoryIds) {
        if (defaultRepositoryIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> counts = new HashMap<>();
        for (RepositoryCount row : issueTrackingRepository
                .countByRepositoryIdsAndStatus(defaultRepositoryIds, IssueStatus.OPEN)) {
            counts.put(row.getRepositoryId(), row.getCnt());
        }
        return counts;
    }

    private Map<String, Map<String, Long>> countTasks(List<String> defaultRepositoryIds, List<String> mainTaskTypes) {
        if (defaultRepositoryIds.isEmpty() || mainTaskTypes.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Long>> counts = new HashMap<>();
        for (TaskTypeCount row : taskInstanceRepository
                .countByRepositoryIdsAndTaskTypes(defaultRepositoryIds, mainTaskTypes)) {
            counts.computeIfAbsent(row.getRepositoryId(), key -> new HashMap<>())
                    .put(row.getTaskType(), row.getCnt());
        }
        return counts;
    }

    /**
     * 批量取主任务类型名称。
     *
     * <p>{@code task_type} 为复合主键 {@code (spec_group_id, code)}，编码仅组内唯一，故名称须按「规格组ID +
     * 编码」定位，不能仅凭 code 匹配。
     */
    private Map<String, String> loadTaskTypeNames(List<WorkbenchComponentRow> rows) {
        List<Long> specGroupIds = rows.stream()
                .map(WorkbenchComponentRow::getSpecGroupId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (specGroupIds.isEmpty()) {
            return Map.of();
        }
        Map<String, String> names = new HashMap<>();
        for (TaskType taskType : taskTypeRepository.findAllBySpecGroupIdIn(specGroupIds)) {
            names.put(taskTypeKey(taskType.getSpecGroupId(), taskType.getCode()), taskType.getName());
        }
        return names;
    }

    private static String taskTypeKey(Long specGroupId, String code) {
        return specGroupId + "|" + code;
    }

    private ComponentCardVO toCard(WorkbenchComponentRow row,
                                   ComponentRepository defaultRepository,
                                   int repositoryCount,
                                   Map<String, Long> blockingIssues,
                                   Map<String, Map<String, Long>> taskCounts,
                                   Map<String, String> taskTypeNames) {
        String mainTaskType = row.getMainTaskType();
        String taskLabel = StringUtils.hasText(mainTaskType)
                ? taskTypeNames.get(taskTypeKey(row.getSpecGroupId(), mainTaskType))
                : null;

        // 任务数量口径：需同时具备默认工程与可解析的主任务类型名称，否则不可计算
        boolean tasksAvailable = defaultRepository != null
                && StringUtils.hasText(mainTaskType)
                && taskLabel != null;
        ComponentCardVO.MetricVO tasks = tasksAvailable
                ? new ComponentCardVO.MetricVO(
                        taskCounts.getOrDefault(defaultRepository.getId(), Map.of())
                                .getOrDefault(mainTaskType, 0L),
                        taskLabel,
                        true,
                        null)
                : new ComponentCardVO.MetricVO(0L, null, false, null);

        // 规则数量口径：PQ-01 未落地，默认工程缺失时优先提示工程不可用
        ComponentCardVO.MetricVO rules = new ComponentCardVO.MetricVO(0L, RULE_LIB_LABEL, false,
                defaultRepository == null ? DEFAULT_REPOSITORY_UNAVAILABLE_REASON : RULE_LIB_UNAVAILABLE_REASON);

        long blockingIssueCount = defaultRepository == null
                ? 0L
                : blockingIssues.getOrDefault(defaultRepository.getId(), 0L);

        RepositoryVO defaultRepositoryVO = defaultRepository == null
                ? null
                : RepositoryVO.forCard(defaultRepository.getId(), defaultRepository.getName(),
                        defaultRepository.getVersion());

        return new ComponentCardVO(
                row.getId(),
                row.getName(),
                row.getIcon(),
                row.getScope(),
                row.getStatus(),
                ComponentStatus.ENABLED.equals(row.getStatus()),
                row.getSpecGroupName(),
                repositoryCount,
                defaultRepositoryVO,
                new ComponentCardVO.MetricsVO((int) blockingIssueCount, tasks, rules));
    }
}
