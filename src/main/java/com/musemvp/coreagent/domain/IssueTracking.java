package com.musemvp.coreagent.domain;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 问题跟踪，对应表 {@code issue_tracking}（数据模型 v1 §2.4）。
 *
 * <p><b>只读部分映射</b>：M01 对该表只做「按工程仓库 + 状态计数」（§2.6.2 步骤 3），故仅映射计数所需的
 * 三列。全部访问均经 {@code IssueTrackingRepository} 的原生 SQL，不产生派生查询，避免因字段不全而生成
 * 缺列语句。标注 {@link Immutable} 表示只读，Hibernate 不做脏检查。
 */
@Entity
@Table(name = "issue_tracking")
@Immutable
public class IssueTracking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "repository_id", length = 64)
    private String repositoryId;

    /** 问题状态；值域见 {@code IssueStatus}（PQ-02）。 */
    @Column(name = "status", nullable = false, length = 8)
    private String status;

    public Long getId() {
        return id;
    }

    public String getRepositoryId() {
        return repositoryId;
    }

    public String getStatus() {
        return status;
    }
}
