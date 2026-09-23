package com.musemvp.coreagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 技术框架，对应表 {@code tech_framework}（数据模型 v1 §2.1）。
 *
 * <p>M01 只读其中的能力开关：{@code orches} 决定「服务编排全景」入口可用性（BR-M01-08），
 * {@code rulelib} 决定「规则库全景」入口可用性（BR-M01-09）。
 */
@Entity
@Table(name = "tech_framework")
public class TechFramework {

    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "language", nullable = false, length = 32)
    private String language;

    @Column(name = "type", nullable = false, length = 8)
    private String type;

    @Column(name = "build_cmd", length = 255)
    private String buildCmd;

    @Column(name = "startup_cmd", length = 255)
    private String startupCmd;

    @Column(name = "builtin", nullable = false)
    private Boolean builtin;

    /** 支持服务编排标识。 */
    @Column(name = "orches", nullable = false)
    private Boolean orches;

    /** 支持规则库标识。 */
    @Column(name = "rulelib", nullable = false)
    private Boolean rulelib;

    @Column(name = "description", length = 255)
    private String description;

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

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getBuildCmd() {
        return buildCmd;
    }

    public void setBuildCmd(String buildCmd) {
        this.buildCmd = buildCmd;
    }

    public String getStartupCmd() {
        return startupCmd;
    }

    public void setStartupCmd(String startupCmd) {
        this.startupCmd = startupCmd;
    }

    public Boolean getBuiltin() {
        return builtin;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public Boolean getOrches() {
        return orches;
    }

    public void setOrches(Boolean orches) {
        this.orches = orches;
    }

    public Boolean getRulelib() {
        return rulelib;
    }

    public void setRulelib(Boolean rulelib) {
        this.rulelib = rulelib;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
