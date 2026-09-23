package com.musemvp.coreagent.api.dto;

import java.util.List;

/**
 * 应用类型字典全量出参（详细设计 D-M04-01/C-01）。
 *
 * <p>字段顺序固定为 {@code types, typeCount, valueCount}，对应契约样例：
 * {@code {"types":[…],"typeCount":9,"valueCount":37}}；空字典为
 * {@code {"types":[],"typeCount":0,"valueCount":0}}（RC-M04-13、D-M04-01/C-04）。
 *
 * <p>{@code typeCount} = 分组数，{@code valueCount} = 全部取值条数（两者均为派生值）。
 * 前端页脚「{typeCount}个类型 · {valueCount}条取值」直接取用这两个字段，不在前端二次统计
 * （D-M04-01/C-03、TC-24）。
 */
public record AppTypeDictionaryVO(List<AppTypeGroupVO> types, int typeCount, int valueCount) {
}
