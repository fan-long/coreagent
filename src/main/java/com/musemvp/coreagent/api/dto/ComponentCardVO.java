package com.musemvp.coreagent.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 工作台组件卡片，见详细设计 §2.3.2（API-M01-02）与 UI-M01-02。
 *
 * <p>全部字段为派生展示信息，不落库（DP-M01-01）。
 *
 * @param enabled            是否启用；{@code false} 时前端置灰但保留点击（BR-M01-02）
 * @param specGroupName      需求规格名称；未关联时为 {@code null}，前端不渲染该展示项
 * @param repositoryCount    该组件工程仓库数量
 * @param defaultRepository  默认工程；不存在时为 {@code null}，前端不渲染默认工程版本
 * @param metrics            三项指标：阻塞事项数、任务数量、规则数量
 */
public record ComponentCardVO(
        String id,
        String name,
        String icon,
        String scope,
        String status,
        boolean enabled,
        String specGroupName,
        int repositoryCount,
        RepositoryVO defaultRepository,
        MetricsVO metrics) {

    public record MetricsVO(int blockingIssues, MetricVO tasks, MetricVO rules) {
    }

    /**
     * 单项指标。
     *
     * @param value             数值；{@code available} 为 {@code false} 时前端仅展示占位
     * @param label             口径名称；{@code null} 表示不渲染口径名（组件未配置主任务类型），
     *                          此时仍显式下发 {@code null} 以便前端区分「无口径名」与「字段缺失」
     * @param available         口径数据是否可计算
     * @param unavailableReason 不可用原因，供悬停提示；可计算时不下发该字段
     */
    public record MetricVO(long value, String label, boolean available,
                           @JsonInclude(JsonInclude.Include.NON_NULL) String unavailableReason) {
    }
}
