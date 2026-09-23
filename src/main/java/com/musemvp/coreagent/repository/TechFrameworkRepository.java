package com.musemvp.coreagent.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.musemvp.coreagent.domain.TechFramework;

/**
 * 技术框架数据访问（§2.6）。
 *
 * <p>M01 只按主键批量取框架（工作台不需要，组件信息导航取当前工程的应用框架与基础框架），使用
 * {@link JpaRepository#findAllById(Iterable)} 即可，无需额外查询方法。
 */
public interface TechFrameworkRepository extends JpaRepository<TechFramework, String> {
}
