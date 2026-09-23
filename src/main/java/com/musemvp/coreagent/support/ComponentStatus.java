package com.musemvp.coreagent.support;

/**
 * {@code component_definition.status} 取值常量。
 *
 * <p>数据模型 v1 未定义该列的值域（PQ-02），「启用在前」排序（BR-M01-01）与卡片置灰（BR-M01-02）依赖该
 * 字面量。集中在此声明，禁止在 SQL 与业务代码中散落字面量；值域确认后只需修改本类。
 */
public final class ComponentStatus {

    /** 启用：参与「启用在前」排序，卡片不下发置灰态。 */
    public static final String ENABLED = "启用";

    private ComponentStatus() {
    }
}
