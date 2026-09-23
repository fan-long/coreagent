package com.musemvp.coreagent.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 项目信息，对应表 {@code project_info}（数据模型 v1 §2.1）。
 *
 * <p>M01 对项目为「读 + 写」：读用于项目列表与当前项目解析，写用于新增与重命名（§4.1）。
 * 表结构由 DBA 脚本维护（{@code ddl-auto: none}）。
 */
@Entity
@Table(name = "project_info")
public class ProjectInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "goal", columnDefinition = "text")
    private String goal;

    @Column(name = "scope", columnDefinition = "text")
    private String scope;

    /** 工程阶段清单，文本列；可能是 JSON 数组或分隔符文本，由服务层解析（§2.3.1）。 */
    @Column(name = "stages", columnDefinition = "text")
    private String stages;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGoal() {
        return goal;
    }

    public void setGoal(String goal) {
        this.goal = goal;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getStages() {
        return stages;
    }

    public void setStages(String stages) {
        this.stages = stages;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
