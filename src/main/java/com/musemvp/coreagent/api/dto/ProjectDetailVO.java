package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 项目定义明细，见详细设计 D-M02-01/C-04（M02）。
 *
 * <p>字段顺序为稳定契约；{@code goal} / {@code scope} 为空时下发 {@code null}（不补空串）；
 * <b>不</b>下发 {@code created_at}（{@code project_info} 无此列，DEF-M02-11）。
 *
 * <p>工程阶段以字符串数组传输（DD-M02-08）：序列化与反序列化全部在服务端完成，前端不拼接、
 * 不解析分隔文本。{@code lockedStages} 与服务端常量同源，供前端渲染只读行，避免前端复制锁定清单；
 * {@code stages} 已按 RC-M02-05 完成空值回落。
 */
public record ProjectDetailVO(
        Long id,
        String name,
        String goal,
        String scope,
        List<String> stages,
        List<String> lockedStages,
        String updatedAt) {
}
