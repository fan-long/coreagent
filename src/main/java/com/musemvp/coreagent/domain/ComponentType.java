package com.musemvp.coreagent.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 应用类型字典，对应表 {@code component_type}（数据模型 v1 §4.1；详细设计 D-M04-11/C-01）。
 *
 * <p>映射与 DEF-M04-11 的六项结构一一对应：{@code id}（bigint 自增代理键）、{@code type}、
 * {@code value}、{@code description}、{@code sort_order}。
 *
 * <p><b>不映射</b> {@code created_at} / {@code updated_at} / 逻辑删除标志 / 版本字段——表中不存在这些列
 * （RC-M04-14 明确对象结构全局条款）。因此本实体<b>不使用</b> {@code @Version} 乐观锁，也不做逻辑删除。
 *
 * <p><b>类型不是独立实体</b>（DEF-M04-02、DD-M04-11）：本模块不为「类型」建立表、实体或仓储，类型的
 * 存在性一律由其取值记录派生（{@code countByType(type) > 0}）。{@code type} 是本表的普通列，不是外键。
 *
 * <p><b>不映射下游关联表</b>：本实体不含 {@code specgroup_comptype_rel} 的任何引用，删除路径因而不存在
 * 关联校验或级联清理的代码路径（RC-M04-12、DD-M04-07、D-M04-11/C-12）。
 *
 * <p>表结构由外部 DDL 维护（{@code spring.jpa.hibernate.ddl-auto: none}），本模块不新增列、索引与唯一键，
 * 也不触发 DDL（DQ-M04-15）。
 */
@Entity
@Table(name = "component_type")
public class ComponentType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 应用类型分类；写入前已按 D-M04-06/C-02 裁剪，非空。 */
    @Column(name = "type", nullable = false, length = 64)
    private String type;

    /** 应用类型值；写入前已按 D-M04-06/C-03 裁剪，非空。 */
    @Column(name = "value", nullable = false, length = 64)
    private String value;

    /** 值说明；空说明按空字符串 {@code ""} 存储，不使用 {@code NULL}（RC-M04-03，D-M04-06/C-04）。 */
    @Column(name = "description", length = 255)
    private String description;

    /** 排序号；历史数据可能为空，空值在升序排序中视为最小（D-M04-07/C-04）。 */
    @Column(name = "sort_order")
    private Integer sortOrder;

    protected ComponentType() {
        // JPA 要求的无参构造；业务侧一律使用下方四参构造
    }

    public ComponentType(String type, String value, String description, Integer sortOrder) {
        this.type = type;
        this.value = value;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
