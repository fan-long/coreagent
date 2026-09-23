package com.musemvp.coreagent.service;

import com.musemvp.coreagent.api.dto.AppTypeGroupVO;
import com.musemvp.coreagent.api.dto.AppTypeValueVO;
import com.musemvp.coreagent.domain.ComponentType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 应用类型字典的<b>内存分组</b>（详细设计 D-M04-07）。
 *
 * <p>分组规则（D-M04-07/C-02）：
 * <ol>
 *   <li>输入是已按 {@code sort_order ASC, id ASC} 排好序的全量行（{@code findAllOrdered()} 的输出）；</li>
 *   <li>用 {@link LinkedHashMap} 顺序累积，<b>类型出现顺序 = 该类型首条记录在排序结果中的位置</b>，
 *       因此左侧类型列表的次序同样由 {@code sort_order} 决定，不引入额外排序规则；</li>
 *   <li>同类型内的取值顺序 = 输入顺序，不再二次排序。</li>
 * </ol>
 *
 * <p>本类不查库、不开事务、不做规范化（规范化在 {@link AppTypeRules}，读路径无需再校验）——
 * 只做「行列表 → 分组列表」的纯变换，可脱离 Spring 上下文单测。
 */
public final class AppTypeGrouping {

    private AppTypeGrouping() {
    }

    /**
     * 把排序后的全量行折叠为分组列表。
     *
     * <p>调用方需保证入参已按其展示顺序排好；空列表返回空分组列表（对应空字典
     * {@code {"types":[],"typeCount":0,"valueCount":0}}，RC-M04-13）。
     */
    public static List<AppTypeGroupVO> group(List<ComponentType> rows) {
        Map<String, List<AppTypeValueVO>> buckets = new LinkedHashMap<>();
        for (ComponentType row : rows) {
            buckets.computeIfAbsent(row.getType(), key -> new ArrayList<>()).add(toValueVO(row));
        }
        List<AppTypeGroupVO> groups = new ArrayList<>(buckets.size());
        buckets.forEach((type, values) -> groups.add(new AppTypeGroupVO(type, values.size(), values)));
        return groups;
    }

    /** 单行出参转换（读取路径）；{@code description} 为 {@code null} 的历史数据归一为空串（RC-M04-03）。 */
    public static AppTypeValueVO toValueVO(ComponentType row) {
        String description = row.getDescription() == null ? "" : row.getDescription();
        return AppTypeValueVO.forRead(row.getId(), row.getValue(), description, row.getSortOrder());
    }
}
