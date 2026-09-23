package com.musemvp.coreagent.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * 任务类型，对应表 {@code task_type}（数据模型 v1 §2.1）。
 *
 * <p>M01 只读该表：主任务类型名称决定卡片任务口径名与「接口设计／数据模型／测试案例」菜单名
 * （BR-M01-06），三类 Agent 字段决定对应入口的可用性（BR-M01-07）。
 */
@Entity
@Table(name = "task_type")
@IdClass(TaskTypeId.class)
public class TaskType {

    @Id
    @Column(name = "spec_group_id")
    private Long specGroupId;

    @Id
    @Column(name = "code", length = 8)
    private String code;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "type", nullable = false, length = 8)
    private String type;

    @Column(name = "template_id")
    private Long templateId;

    /** 内容提取 Agent 标识 → agent_definition.id。 */
    @Column(name = "extract_agent", length = 64)
    private String extractAgent;

    /** 任务条目规划 Agent 标识 → agent_definition.id。 */
    @Column(name = "plan_agent", length = 64)
    private String planAgent;

    /** 辅助 Agent 标识 → agent_definition.id。 */
    @Column(name = "assist_agent", length = 64)
    private String assistAgent;

    @Column(name = "sort_order")
    private Integer sortOrder;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getSpecGroupId() {
        return specGroupId;
    }

    public void setSpecGroupId(Long specGroupId) {
        this.specGroupId = specGroupId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getExtractAgent() {
        return extractAgent;
    }

    public void setExtractAgent(String extractAgent) {
        this.extractAgent = extractAgent;
    }

    public String getPlanAgent() {
        return planAgent;
    }

    public void setPlanAgent(String planAgent) {
        this.planAgent = planAgent;
    }

    public String getAssistAgent() {
        return assistAgent;
    }

    public void setAssistAgent(String assistAgent) {
        this.assistAgent = assistAgent;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
