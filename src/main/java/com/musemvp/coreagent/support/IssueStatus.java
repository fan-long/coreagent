package com.musemvp.coreagent.support;

/**
 * {@code issue_tracking.status} 取值常量。
 *
 * <p>数据模型 v1 未定义该列的值域（PQ-02），「阻塞事项数」口径依赖「打开」状态的字面量。集中在此声明，
 * 禁止在 SQL 与业务代码中散落字面量；值域确认后只需修改本类。
 */
public final class IssueStatus {

    /** 打开：计入卡片「阻塞事项数」与「问题跟踪」菜单角标。 */
    public static final String OPEN = "打开";

    private IssueStatus() {
    }
}
