package com.musemvp.coreagent.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.musemvp.coreagent.domain.TaskType;
import com.musemvp.coreagent.domain.TaskTypeId;

/**
 * 任务类型数据访问（§2.6）。
 *
 * <p>主键为复合主键 {@code (spec_group_id, code)}，跨组查询须携带组维度（数据模型 v1 §2.1）。
 */
public interface TaskTypeRepository extends JpaRepository<TaskType, TaskTypeId> {

    /** 组件主任务类型对应的任务类型记录：取名称（BR-M01-06）与三类 Agent 配置（BR-M01-07）。 */
    Optional<TaskType> findBySpecGroupIdAndCode(Long specGroupId, String code);

    /** 工作台批量取主任务类型名称，避免逐组件查询。 */
    List<TaskType> findAllBySpecGroupIdIn(Collection<Long> specGroupIds);
}
