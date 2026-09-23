package com.musemvp.coreagent.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.musemvp.coreagent.domain.ProjectInfo;

/**
 * 项目信息数据访问（§2.6）。
 */
public interface ProjectInfoRepository extends JpaRepository<ProjectInfo, Long> {

    /**
     * 项目列表查询，排序 {@code id ASC}（RC-M02-12，DD-M02-12）。
     *
     * <p>原实现为 {@code updated_at DESC, id DESC}；M02 按「必须按项目 ID 升序返回」修改。
     * 该查询同时服务 M01 的项目列表弹窗与全局上下文，次序变化会同时影响两处（见第 8 节、DQ-M02-16）。
     */
    @Query("SELECT p FROM ProjectInfo p ORDER BY p.id ASC")
    List<ProjectInfo> findAllOrdered();

    /** 当前项目兜底：{@code X-Project-Id} 缺失、非法或指向已删除项目时取 {@code id} 最小的一条（§2.4）。 */
    Optional<ProjectInfo> findFirstByOrderByIdAsc();

    /** 唯一性校验：名称在 {@code project_info} 中不重复（§2.3.5）。 */
    boolean existsByName(String name);

    /** 重命名时的唯一性校验：排除自身。 */
    boolean existsByNameAndIdNot(String name, Long id);
}
