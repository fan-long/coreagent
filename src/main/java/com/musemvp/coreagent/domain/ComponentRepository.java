package com.musemvp.coreagent.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 组件工程仓库，对应表 {@code component_repository}（数据模型 v1 §2.4）。
 *
 * <p>M01 只读该表：工作台卡片取工程数量与默认工程版本，组件信息侧边栏取工程选择器与框架能力（§2.5）。
 *
 * <p>注意：{@code (component_id, is_default)} 未建唯一约束，「默认工程唯一」由应用层保证（PQ-05），
 * 因此取默认工程的逻辑集中在服务层单点实现，不在查询中散落。
 */
@Entity
@Table(name = "component_repository")
public class ComponentRepository {

    @Id
    @Column(name = "id", length = 64)
    private String id;

    @Column(name = "component_id", nullable = false, length = 32)
    private String componentId;

    @Column(name = "name", length = 64)
    private String name;

    @Column(name = "status", length = 16)
    private String status;

    @Column(name = "branch", length = 64)
    private String branch;

    @Column(name = "code_path", length = 256)
    private String codePath;

    @Column(name = "top_pkg", length = 128)
    private String topPkg;

    @Column(name = "language", length = 32)
    private String language;

    @Column(name = "version", length = 16)
    private String version;

    @Column(name = "model_version", length = 32)
    private String modelVersion;

    /** 应用框架标识 → tech_framework.id，决定「服务编排全景」入口可用性（BR-M01-08）。 */
    @Column(name = "app_framework", length = 64)
    private String appFramework;

    /** 基础框架标识 → tech_framework.id，决定「规则库全景」入口可用性（BR-M01-09）。 */
    @Column(name = "base_framework", length = 64)
    private String baseFramework;

    @Column(name = "confirm_mode", length = 16)
    private String confirmMode;

    /** DDL 为 {@code bit(1) NOT NULL}，JPA 映射为 Boolean（§2.6.1）。 */
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault;

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

    public String getComponentId() {
        return componentId;
    }

    public void setComponentId(String componentId) {
        this.componentId = componentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCodePath() {
        return codePath;
    }

    public void setCodePath(String codePath) {
        this.codePath = codePath;
    }

    public String getTopPkg() {
        return topPkg;
    }

    public void setTopPkg(String topPkg) {
        this.topPkg = topPkg;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getAppFramework() {
        return appFramework;
    }

    public void setAppFramework(String appFramework) {
        this.appFramework = appFramework;
    }

    public String getBaseFramework() {
        return baseFramework;
    }

    public void setBaseFramework(String baseFramework) {
        this.baseFramework = baseFramework;
    }

    public String getConfirmMode() {
        return confirmMode;
    }

    public void setConfirmMode(String confirmMode) {
        this.confirmMode = confirmMode;
    }

    public Boolean getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Boolean isDefault) {
        this.isDefault = isDefault;
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
