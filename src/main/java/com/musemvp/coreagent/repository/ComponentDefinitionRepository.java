package com.musemvp.coreagent.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.musemvp.coreagent.domain.ComponentDefinition;

/**
 * 应用组件数据访问（§2.6）。
 */
public interface ComponentDefinitionRepository extends JpaRepository<ComponentDefinition, String> {

    /**
     * 工作台卡片步骤 1：组件 + 规格组名（§2.6.2）。
     *
     * <p>一块查询取齐组件基础信息与规格组名，避免逐组件查询的 N+1；排序按 BR-M01-01「启用在前、未启用
     * 在后」，组内 {@code updated_at DESC, id ASC}。启用状态以参数绑定，字面量集中在 {@code ComponentStatus}
     * （PQ-02）。
     *
     * <p>索引建议见 PQ-04：{@code component_definition.project_id} 当前无索引。
     */
    @Query(value = """
            SELECT c.id AS id,
                   c.name AS name,
                   c.icon AS icon,
                   c.scope AS scope,
                   c.status AS status,
                   c.spec_group_id AS specGroupId,
                   c.main_task_type AS mainTaskType,
                   g.name AS specGroupName
            FROM component_definition c
            LEFT JOIN spec_group_definition g ON g.id = c.spec_group_id
            WHERE c.project_id = :projectId
            ORDER BY CASE WHEN c.status = :enabledStatus THEN 0 ELSE 1 END, c.updated_at DESC, c.id ASC
            """, nativeQuery = true)
    List<WorkbenchComponentRow> findWorkbenchRows(@Param("projectId") Long projectId,
                                                  @Param("enabledStatus") String enabledStatus);

    /** 工作台卡片步骤 1 的行投影。 */
    interface WorkbenchComponentRow {

        String getId();

        String getName();

        String getIcon();

        String getScope();

        String getStatus();

        /** 需求规格组ID，用于在步骤 5 定位主任务类型名称（task_type 复合主键需携带组维度）。 */
        Long getSpecGroupId();

        String getMainTaskType();

        /** 需求规格名称；未关联规格组或规格组被删除时为 {@code null}。 */
        String getSpecGroupName();
    }
}
