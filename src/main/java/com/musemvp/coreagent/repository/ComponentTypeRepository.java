package com.musemvp.coreagent.repository;

import com.musemvp.coreagent.domain.ComponentType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * {@code component_type} 的数据访问入口（详细设计 D-M04-11/C-02）。
 *
 * <p><b>一次全量读取</b>：读路径只有 {@link #findAllOrdered()} 一条查询，分组与全字典计数均在内存完成，
 * 不产生按类型重复查询的 N+1（DD-M04-02、D-M04-07/C-01）。
 *
 * <p><b>JPQL 一律使用属性路径</b>（{@code c.type} / {@code c.value}），不写裸列名——{@code type} 与
 * {@code value} 在 JPQL 中是保留语义敏感词，裸写会被解析为非法的路径表达式（D-M04-11/C-11）。
 *
 * <p>仓储不新增任何 DDL 操作；表结构由外部 DDL 维护（{@code ddl-auto: none}，DQ-M04-15）。
 */
public interface ComponentTypeRepository extends JpaRepository<ComponentType, Long> {

    /**
     * 读取全量字典行，排序为 {@code sort_order ASC, id ASC}。
     *
     * <p>两条排序键缺一不可：{@code sort_order} 承担展示顺序（RC-M04-06），{@code id} 承担并列时的
     * 稳定次序（D-M04-07/C-04）。{@code sort_order} 为 {@code NULL} 的历史行在 MySQL 升序中排最前，
     * 即「空值视为最小」，与设计一致，因此不加 {@code IS NULL} 兜底表达式。
     */
    @Query("SELECT c FROM ComponentType c ORDER BY c.sortOrder ASC, c.id ASC")
    List<ComponentType> findAllOrdered();

    /** 类型下取值条数；类型存在性由本方法的返回值派生（类型不是实体，DEF-M04-02）。 */
    long countByType(String type);

    /** 同类型下是否已存在该取值，用于新增去重（D-M04-06/C-05）。 */
    boolean existsByTypeAndValue(String type, String value);

    /** 同类型下是否存在<b>其他</b>记录占用该取值，用于编辑去重时排除自身（D-M04-06/C-06）。 */
    boolean existsByTypeAndValueAndIdNot(String type, String value, Long id);

    /**
     * 当前表内最大排序号，用于新增时取 {@code MAX(sort_order) + 1}（D-M04-07/C-03）。
     *
     * <p>空表或全空 {@code sort_order} 时返回 {@code null}，由调用方落到 1。
     */
    @Query("SELECT MAX(c.sortOrder) FROM ComponentType c")
    Integer findMaxSortOrder();

    /**
     * 按类型名删除其下全部取值，返回实际受影响行数（RC-M04-08、D-M04-09/C-02）。
     *
     * <p>用 {@code @Modifying} 批量删除而非「逐个 load 后 delete」，避免 N 次删除语句；
     * 返回值为受影响行数，直接充当响应中的 {@code deletedCount}（D-M04-09/P-03 采用其返回值，
     * 不再另行 {@code countByType}）。
     *
     * <p>删除范围为 {@code component_type} 单表，<b>不触碰</b> {@code specgroup_comptype_rel}
     * 等下游关联表（RC-M04-12、DD-M04-07、D-M04-09/C-03）。
     */
    @Modifying
    @Query("DELETE FROM ComponentType c WHERE c.type = :type")
    int deleteByType(@Param("type") String type);
}
