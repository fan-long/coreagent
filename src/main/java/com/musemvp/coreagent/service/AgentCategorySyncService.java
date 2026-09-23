package com.musemvp.coreagent.service;

import java.time.LocalDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.musemvp.coreagent.repository.AgentDefinitionRepository;

/**
 * 阶段改名判定与 Agent 归属迁移构件，见详细设计 D-M02-04（M02）。
 *
 * <p>以<b>库中存储值</b>解析出的阶段集合为基准（DD-M02-04），在「恰好删除 1 个且新增 1 个」时
 * 判定为阶段改名，并把该阶段下全部 Agent 的一级分类归属迁移为新阶段名（RC-M02-09）。
 *
 * <p>边界：不写 {@code agent_category}（DD-M02-05/RC-M02-11）、不改写工作流阶段与节点
 * （RC-M02-10 的例外）、不维护 Agent 定义本身（M10）、不处理非「1 删 1 增」的多阶段增删
 * （Q-M02-01 未规定，见 DQ-M02-01）。
 *
 * <p>本构件<b>不自行开启事务</b>：运行在保存路径的事务内，迁移异常即整体回滚，阶段与 Agent 分类
 * 保持变更前状态（RC-M02-17，DD-M02-06）。
 */
@Service
public class AgentCategorySyncService {

    private static final Logger log = LoggerFactory.getLogger(AgentCategorySyncService.class);

    private final AgentDefinitionRepository agentDefinitionRepository;

    public AgentCategorySyncService(AgentDefinitionRepository agentDefinitionRepository) {
        this.agentDefinitionRepository = agentDefinitionRepository;
    }

    /**
     * 迁移结论。
     *
     * @param renamed      是否判定为改名
     * @param oldName      原阶段名（未改名时为 {@code null}）
     * @param newName      新阶段名（未改名时为 {@code null}）
     * @param affectedRows 受影响行数，仅用于日志，不参与判定
     */
    public record SyncResult(boolean renamed, String oldName, String newName, int affectedRows) {

        private static final SyncResult NONE = new SyncResult(false, null, null, 0);
    }

    /**
     * 判定并执行阶段改名的 Agent 归属迁移（D-M02-04/C-01…C-03）。
     *
     * <p>判定输入的两个集合必须来自不同来源：{@code storedStages} 取自数据库存储值，
     * {@code targetStages} 取自本次请求。调用方必须已完成锁定阶段校验（D-M02-04/C-05）。
     *
     * @param storedStages 当前行的 {@code stages} 存储值经 {@code ProjectStageService#parse} 的结果
     * @param targetStages 本次保存经 {@code ProjectStageService#normalize} 后的集合
     * @return 迁移结论；非改名分支不产生任何 Agent 侧写入
     */
    public SyncResult sync(List<String> storedStages, List<String> targetStages) {
        List<String> removed = storedStages.stream().filter(stage -> !targetStages.contains(stage)).toList();
        List<String> added = targetStages.stream().filter(stage -> !storedStages.contains(stage)).toList();

        // 恰好 1 删 1 增才判定为改名；「无变化」「仅删除」「仅新增」「2 删 2 增」等一律不迁移
        if (removed.size() != 1 || added.size() != 1) {
            return SyncResult.NONE;
        }

        String oldName = removed.get(0);
        String newName = added.get(0);
        // 按阶段名原值写入，不截断；超出 agent_definition.category 列宽时由数据库拒绝并整体回滚（DD-M02-09）
        int affectedRows = agentDefinitionRepository.migrateCategory(oldName, newName, LocalDateTime.now());
        log.info("阶段改名迁移 Agent 归属 old={} new={} affectedRows={}", oldName, newName, affectedRows);
        return new SyncResult(true, oldName, newName, affectedRows);
    }
}
