package com.musemvp.coreagent.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.musemvp.coreagent.domain.IssueTracking;

/**
 * 问题跟踪数据访问（§2.6）。
 *
 * <p>M01 只做计数，全部为原生聚合查询；状态以参数绑定，字面量集中在 {@code IssueStatus}（PQ-02）。
 * 索引建议见 PQ-04：当前仅有 {@code idx_issue_item_code(item_code)}，不覆盖按仓库 + 状态的聚合。
 */
public interface IssueTrackingRepository extends JpaRepository<IssueTracking, Long> {

    /**
     * 工作台卡片步骤 3：按默认工程分组统计打开状态问题数（§2.6.2）。
     *
     * @param repositoryIds 非空集合，调用方须先过滤空集合
     */
    @Query(value = """
            SELECT i.repository_id AS repositoryId, COUNT(*) AS cnt
            FROM issue_tracking i
            WHERE i.repository_id IN (:repositoryIds) AND i.status = :status
            GROUP BY i.repository_id
            """, nativeQuery = true)
    List<RepositoryCount> countByRepositoryIdsAndStatus(
            @Param("repositoryIds") Collection<String> repositoryIds,
            @Param("status") String status);

    /** 「问题跟踪」菜单角标：当前工程的打开状态问题数。 */
    @Query(value = """
            SELECT COUNT(*) FROM issue_tracking i
            WHERE i.repository_id = :repositoryId AND i.status = :status
            """, nativeQuery = true)
    long countByRepositoryAndStatus(@Param("repositoryId") String repositoryId,
                                    @Param("status") String status);

    /** 聚合行投影。 */
    interface RepositoryCount {

        String getRepositoryId();

        long getCnt();
    }
}
