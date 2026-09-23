package com.musemvp.coreagent.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 任务实例，对应表 {@code task_instance}（数据模型 v1 §2.4）。
 *
 * <p><b>只读部分映射</b>：M01 对该表只做「按工程仓库 + 任务类型计数」（§2.6.2 步骤 4 与菜单角标），故仅
 * 映射计数所需的三列。全部访问均经 {@code TaskInstanceRepository} 的原生 SQL，不产生派生查询，避免因
 * 字段不全而生成缺列语句。标注 {@link Immutable} 表示只读，Hibernate 不做脏检查。
 */
@Entity
@Table(name = "task_instance")
@Immutable
public class TaskInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "repository_id", nullable = false, length = 64)
    private String repositoryId;

    /** 任务类型编码，仅组内唯一，跨组须配合 repository_id 消歧（数据模型 v1 §2.1）。 */
    @Column(name = "task_type", nullable = false, length = 8)
    private String taskType;

    public Long getId() {
        return id;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public String getTaskType() {
        return taskType;
    }
}
