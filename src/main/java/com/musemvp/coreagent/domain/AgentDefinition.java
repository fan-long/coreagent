package com.musemvp.coreagent.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Agent 定义的<b>最小映射</b>，见详细设计 D-M02-10/C-06（M02）。
 *
 * <p>只映射阶段改名迁移所需的三列：{@code id}（标识）、{@code category}（条件与赋值）、
 * {@code updated_at}（时间戳）。本实体<b>不参与插入</b>，也不承载 Agent 领域模型——
 * Agent 的完整定义（提示词、模型、工具等）属 M10 范围，且其正文内容不进入 M02 的读写面（D-M02-10/4.5）。
 *
 * <p>M10 实施完整模型后须评估合并本映射，避免同一表存在两套实体（第 8 节）。
 */
@Entity
@Table(name = "agent_definition")
public class AgentDefinition {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    /** 一级分类，逻辑引用 {@code agent_category.category}；列宽 {@code varchar(16)} 见 DQ-M02-08。 */
    @Column(name = "category", nullable = false, length = 16)
    private String category;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
