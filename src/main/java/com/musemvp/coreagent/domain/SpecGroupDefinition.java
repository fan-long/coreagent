package com.musemvp.coreagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Spec模板组，对应表 {@code spec_group_definition}（数据模型 v1 §2.1）。
 *
 * <p>M01 只读其名称：卡片「需求规格」展示项与「需求文档」菜单命名（BR-M01-05）均取自本表。
 */
@Entity
@Table(name = "spec_group_definition")
public class SpecGroupDefinition {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
