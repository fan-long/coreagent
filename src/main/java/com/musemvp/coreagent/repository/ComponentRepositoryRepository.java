package com.musemvp.coreagent.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.musemvp.coreagent.domain.ComponentRepository;

/**
 * 组件工程仓库数据访问（§2.6）。
 *
 * <p>「取默认工程」的口径由 {@code RepositorySelection} 单点实现，本接口只负责按 {@code is_default DESC,
 * updated_at DESC} 返回候选序列，避免同组件多默认工程（PQ-05）时各处取值不一致。
 */
public interface ComponentRepositoryRepository extends JpaRepository<ComponentRepository, String> {

    /** 工作台卡片步骤 2：批量取工程仓库，在内存中按组件分组（§2.6.2）。 */
    List<ComponentRepository> findByComponentIdIn(Collection<String> componentIds);

    /**
     * 组件信息导航：单一组件的全部工程仓库，默认工程在前（§2.6.3）。
     *
     * <p>{@code is_default} 在 DDL 中为 {@code bit(1)}，排序语义为「默认工程优先」。
     */
    @Query(value = """
            SELECT * FROM component_repository r
            WHERE r.component_id = :componentId
            ORDER BY r.is_default DESC, r.updated_at DESC
            """, nativeQuery = true)
    List<ComponentRepository> findAllOrdered(@Param("componentId") String componentId);
}
