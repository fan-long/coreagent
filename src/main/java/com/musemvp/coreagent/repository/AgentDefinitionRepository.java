package com.musemvp.coreagent.repository;

import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.musemvp.coreagent.domain.AgentDefinition;

/**
 * Agent 定义的迁移写入，见详细设计 D-M02-10/C-06（M02）。
 *
 * <p>只提供阶段改名所需的批量更新：{@code agent_definition} 中 {@code category} 等于原阶段名的
 * 全部 Agent 改为新阶段名。本仓储<b>不</b>映射 {@code agent_category}（DD-M02-05：阶段派生分类不落表），
 * 也不提供插入与删除。
 */
public interface AgentDefinitionRepository extends JpaRepository<AgentDefinition, String> {

    /**
     * 把 {@code oldCategory} 下的全部 Agent 迁移到 {@code newCategory}。
     *
     * <p>迁入名按阶段名<b>原值</b>写入，不截断、不做字符替换（DD-M02-09）：目标列为
     * {@code varchar(16)}，超出列宽时由数据库拒绝，异常向上抛出并由调用方事务整体回滚（RC-M02-17）。
     *
     * @return 受影响行数；为 0 表示该阶段下无 Agent，属正常结果而非失败
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE AgentDefinition a SET a.category = :newCategory, a.updatedAt = :updatedAt "
            + "WHERE a.category = :oldCategory")
    int migrateCategory(@Param("oldCategory") String oldCategory,
                        @Param("newCategory") String newCategory,
                        @Param("updatedAt") LocalDateTime updatedAt);
}
