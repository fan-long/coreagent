package com.musemvp.coreagent.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.musemvp.coreagent.domain.TaskInstance;

/**
 * 任务实例数据访问（§2.6）。
 *
 * <p>M01 只做计数，全部为原生聚合查询。{@code uk_task_repo_item(repository_id, item_code)} 的前缀可覆盖
 * 按仓库过滤的部分，按任务类型的聚合仍建议补索引（PQ-04）。
 */
public interface TaskInstanceRepository extends JpaRepository<TaskInstance, Long> {

    /**
     * 工作台卡片步骤 4：按默认工程 + 主任务类型分组统计任务数（§2.6.2）。
     *
     * @param repositoryIds 非空集合，调用方须先过滤空集合
     * @param taskTypes     非空集合，调用方须先过滤空集合
     */
    @Query(value = """
            SELECT t.repository_id AS repositoryId, t.task_type AS taskType, COUNT(*) AS cnt
            FROM task_instance t
            WHERE t.repository_id IN (:repositoryIds) AND t.task_type IN (:taskTypes)
            GROUP BY t.repository_id, t.task_type
            """, nativeQuery = true)
    List<TaskTypeCount> countByRepositoryIdsAndTaskTypes(
            @Param("repositoryIds") Collection<String> repositoryIds,
            @Param("taskTypes") Collection<String> taskTypes);

    /** 「任务管理」菜单角标：当前工程 + 主任务类型的任务数。 */
    @Query(value = """
            SELECT COUNT(*) FROM task_instance t
            WHERE t.repository_id = :repositoryId AND t.task_type = :taskType
            """, nativeQuery = true)
    long countByRepositoryAndTaskType(@Param("repositoryId") String repositoryId,
                                      @Param("taskType") String taskType);

    /** 聚合行投影。 */
    interface TaskTypeCount {

        String getRepositoryId();

        String getTaskType();

        long getCnt();
    }
}
