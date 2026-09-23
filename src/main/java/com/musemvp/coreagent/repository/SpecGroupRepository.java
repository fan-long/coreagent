package com.musemvp.coreagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.musemvp.coreagent.domain.SpecGroupDefinition;

/**
 * Spec模板组数据访问（§2.6）。
 *
 * <p>M01 只按主键取规格组名称：组件信息导航的「需求文档」菜单命名（BR-M01-05）与卡片「需求规格」展示项。
 */
public interface SpecGroupRepository extends JpaRepository<SpecGroupDefinition, Long> {
}
