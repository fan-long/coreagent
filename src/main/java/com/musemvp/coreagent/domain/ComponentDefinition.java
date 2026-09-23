package com.musemvp.coreagent.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 应用组件定义，对应表 {@code component_definition}（数据模型 v1 §2.1）。
 *
 * <p>M01 只读该表：工作台卡片（§2.5.1）与组件信息上下文（§2.5.2）均由此派生。
 */
@Entity
@Table(name = "component_definition")
public class ComponentDefinition {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "scope", nullable = false, length = 512)
    private String scope;

    @Column(name = "boundary", length = 512)
    private String boundary;

    /** 组件状态；值域见 {@code ComponentStatus}（PQ-02）。 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "origin", nullable = false, length = 16)
    private String origin;

    @Column(name = "icon", length = 32)
    private String icon;

    /** 需求规格组ID → spec_group_definition.id。 */
    @Column(name = "spec_group_id")
    private Long specGroupId;

    /** 主任务类型编码，关联本规格组下的任务类型 code；为空时工作台不展示任务口径名。 */
    @Column(name = "main_task_type", length = 32)
    private String mainTaskType;

    /** 所属项目id → project_info.id。 */
    @Column(name = "project_id")
    private Long projectId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getBoundary() {
        return boundary;
    }

    public void setBoundary(String boundary) {
        this.boundary = boundary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Long getSpecGroupId() {
        return specGroupId;
    }

    public void setSpecGroupId(Long specGroupId) {
        this.specGroupId = specGroupId;
    }

    public String getMainTaskType() {
        return mainTaskType;
    }

    public void setMainTaskType(String mainTaskType) {
        this.mainTaskType = mainTaskType;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
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
